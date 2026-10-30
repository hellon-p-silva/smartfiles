package com.smartfiles.app.ui.whatsapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfiles.app.data.FileRepository
import com.smartfiles.app.data.index.AppDatabase
import com.smartfiles.app.data.index.FileIndexer
import com.smartfiles.app.data.whatsapp.WaCategory
import com.smartfiles.app.data.whatsapp.WaFile
import com.smartfiles.app.data.whatsapp.WhatsAppScanner
import com.smartfiles.app.util.formatSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

enum class AgeFilter(val label: String, val days: Int) {
    ALL("Tudo", 0),
    D30("+30 dias", 30),
    D90("+3 meses", 90),
    D365("+1 ano", 365);

    fun accepts(lastModified: Long, now: Long): Boolean =
        days == 0 || lastModified < now - TimeUnit.DAYS.toMillis(days.toLong())
}

enum class WaSort(val label: String) { LARGEST("Maiores primeiro"), OLDEST("Mais antigos primeiro"), NEWEST("Mais recentes primeiro") }

data class CategorySummary(val category: WaCategory, val count: Int, val bytes: Long)

data class WaState(
    val loading: Boolean = true,
    val installed: Boolean = true,
    val files: List<WaFile> = emptyList(),
    val age: AgeFilter = AgeFilter.ALL,
    val includeSent: Boolean = true,
    val category: WaCategory? = null,
    val sort: WaSort = WaSort.LARGEST,
    val selected: Set<String> = emptySet(),
    val deleting: Boolean = false,
) {
    private val now = System.currentTimeMillis()

    /** Arquivos que passam nos filtros de idade e "enviados". */
    val filtered: List<WaFile> = files.filter { (includeSent || !it.sent) && age.accepts(it.lastModified, now) }

    val summaries: List<CategorySummary> = filtered
        .groupBy { it.category }
        .map { (cat, list) -> CategorySummary(cat, list.size, list.sumOf { it.size }) }
        .sortedByDescending { it.bytes }

    /** Itens da categoria aberta, já ordenados. */
    val inCategory: List<WaFile> = if (category == null) emptyList() else {
        val list = filtered.filter { it.category == category }
        when (sort) {
            WaSort.LARGEST -> list.sortedByDescending { it.size }
            WaSort.OLDEST -> list.sortedBy { it.lastModified }
            WaSort.NEWEST -> list.sortedByDescending { it.lastModified }
        }
    }

    val selectedBytes: Long = files.filter { it.path in selected }.sumOf { it.size }
}

class WhatsAppViewModel(private val app: Application) : AndroidViewModel(app) {

    private val scanner = WhatsAppScanner()
    private val db = AppDatabase.get(app)
    private val repo = FileRepository(FileIndexer(db.files()))

    private val _state = MutableStateFlow(WaState())
    val state: StateFlow<WaState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val installed = scanner.isInstalled()
            val files = if (installed) scanner.scan() else emptyList()
            _state.update { it.copy(loading = false, installed = installed, files = files, selected = emptySet()) }
        }
    }

    fun setAge(age: AgeFilter) = _state.update { it.copy(age = age, selected = emptySet()) }

    fun toggleSent() = _state.update { it.copy(includeSent = !it.includeSent, selected = emptySet()) }

    fun openCategory(category: WaCategory) = _state.update { it.copy(category = category, selected = emptySet()) }

    fun closeCategory() = _state.update { it.copy(category = null, selected = emptySet()) }

    fun setSort(sort: WaSort) = _state.update { it.copy(sort = sort) }

    fun toggle(path: String) = _state.update {
        it.copy(selected = if (path in it.selected) it.selected - path else it.selected + path)
    }

    /** Marca tudo da categoria aberta; se já estiver tudo marcado, desmarca. */
    fun toggleSelectAll() = _state.update { s ->
        val all = s.inCategory.map { it.path }.toSet()
        s.copy(selected = if (s.selected.containsAll(all) && all.isNotEmpty()) emptySet() else all)
    }

    fun deleteSelected() {
        val s = _state.value
        if (s.selected.isEmpty() || s.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            val deleted = mutableSetOf<String>()
            var freed = 0L
            var failures = 0
            val byPath = s.files.associateBy { it.path }

            for (path in s.selected) {
                repo.delete(File(path))
                    .onSuccess { deleted += path; freed += byPath[path]?.size ?: 0L }
                    .onFailure { failures++ }
            }

            _state.update {
                it.copy(
                    files = it.files.filterNot { f -> f.path in deleted },
                    selected = it.selected - deleted,
                    deleting = false,
                )
            }
            _message.value = buildString {
                append("${formatSize(app, freed)} liberados")
                if (failures > 0) append(" · $failures não puderam ser excluídos")
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
