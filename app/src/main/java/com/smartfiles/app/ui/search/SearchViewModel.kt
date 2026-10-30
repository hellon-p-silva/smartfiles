package com.smartfiles.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.smartfiles.app.data.FileKind
import com.smartfiles.app.data.index.AppDatabase
import com.smartfiles.app.data.index.IndexWorker
import com.smartfiles.app.data.index.SearchHit
import com.smartfiles.app.data.index.normalizeText
import com.smartfiles.app.data.ocr.OcrWorker
import com.smartfiles.app.data.smart.QueryParser
import com.smartfiles.app.data.smart.QueryPlan
import com.smartfiles.app.data.smart.SearchQueryBuilder
import com.smartfiles.app.data.smart.SmartTag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Calendar

enum class DateFilter(val label: String) {
    ANY("Qualquer data"),
    TODAY("Hoje"),
    WEEK("7 dias"),
    MONTH("30 dias"),
    YEAR("Este ano");

    fun since(): Long {
        val cal = Calendar.getInstance()
        return when (this) {
            ANY -> 0L
            TODAY -> cal.startOfDay().timeInMillis
            WEEK -> cal.apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
            MONTH -> cal.apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
            YEAR -> cal.startOfDay().apply { set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        }
    }

    private fun Calendar.startOfDay() = apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}

data class SearchFilters(
    val query: String = "",
    val kind: FileKind? = null,
    val date: DateFilter = DateFilter.ANY,
    val tag: SmartTag? = null,
) {
    val isDefault: Boolean
        get() = query.isBlank() && kind == null && date == DateFilter.ANY && tag == null
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.files()

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    /** O que o app entendeu da frase digitada (mostrado como "Entendi: ..."). */
    val plan: StateFlow<QueryPlan> = _filters
        .map { QueryParser.parse(it.query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueryPlan())

    val results: StateFlow<List<SearchHit>> = _filters
        .debounce(200) // espera o usuário parar de digitar
        .flatMapLatest { f ->
            val plan = QueryParser.parse(f.query)
            dao.search(
                SearchQueryBuilder.build(
                    plan = plan,
                    chipKind = f.kind,
                    chipSince = if (f.date == DateFilter.ANY) null else f.date.since(),
                    chipTag = f.tag,
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Quantos arquivos há em cada categoria automática. */
    val tagCounts: StateFlow<Map<SmartTag, Int>> = db.analysis().allTags()
        .map { rows ->
            val counts = mutableMapOf<SmartTag, Int>()
            rows.forEach { row -> SmartTag.parse(row).forEach { counts[it] = (counts[it] ?: 0) + 1 } }
            counts
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val indexedCount: StateFlow<Int> = dao.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** null = não está indexando; número = itens processados até agora. */
    val indexingProgress: StateFlow<Int?> = WorkManager.getInstance(app)
        .getWorkInfosForUniqueWorkFlow(IndexWorker.UNIQUE_NOW)
        .map { infos ->
            infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                ?.progress?.getInt(IndexWorker.KEY_COUNT, 0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Progresso da análise (OCR + categorias): (feitos, total) enquanto roda; null quando parado. */
    val ocrProgress: StateFlow<Pair<Int, Int>?> = WorkManager.getInstance(app)
        .getWorkInfosForUniqueWorkFlow(OcrWorker.UNIQUE)
        .map { infos ->
            infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress?.let {
                it.getInt(OcrWorker.KEY_DONE, 0) to it.getInt(OcrWorker.KEY_TOTAL, 0)
            }?.takeIf { it.second > 0 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(q: String) = _filters.update { it.copy(query = q) }

    fun setKind(kind: FileKind?) = _filters.update { it.copy(kind = if (it.kind == kind) null else kind) }

    fun setDate(date: DateFilter) = _filters.update { it.copy(date = date) }

    fun setTag(tag: SmartTag) = _filters.update { it.copy(tag = if (it.tag == tag) null else tag) }

    /** Atalho dos exemplos: preenche a busca com uma frase pronta. */
    fun useExample(text: String) = _filters.update { SearchFilters(query = text) }

    fun reindex() = IndexWorker.reindexNow(app)
}

/** Mesma normalização do índice + espaços colapsados (o texto do OCR também é guardado assim). */
fun normalizeQuery(q: String): String = normalizeText(q.trim()).replace("\\s+".toRegex(), " ")
