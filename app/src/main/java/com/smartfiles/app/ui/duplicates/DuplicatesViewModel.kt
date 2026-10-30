package com.smartfiles.app.ui.duplicates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfiles.app.data.FileRepository
import com.smartfiles.app.data.duplicates.DuplicateFinder
import com.smartfiles.app.data.duplicates.DuplicateGroup
import com.smartfiles.app.data.index.AppDatabase
import com.smartfiles.app.data.index.FileIndexer
import com.smartfiles.app.util.formatSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface DupState {
    data class Scanning(val done: Int, val total: Int) : DupState
    data class Ready(val groups: List<DuplicateGroup>) : DupState
}

class DuplicatesViewModel(private val app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val finder = DuplicateFinder(db.files(), db.hashes())
    private val repo = FileRepository(FileIndexer(db.files()))

    private val _state = MutableStateFlow<DupState>(DupState.Scanning(0, 0))
    val state: StateFlow<DupState> = _state.asStateFlow()

    /** Caminhos marcados para excluir. */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private var scanJob: Job? = null

    init {
        scan()
    }

    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.value = DupState.Scanning(0, 0)
            val groups = finder.find { done, total -> _state.value = DupState.Scanning(done, total) }
            _state.value = DupState.Ready(groups)
            _selected.value = autoSelection(groups)
        }
    }

    private fun groups(): List<DuplicateGroup> = (_state.value as? DupState.Ready)?.groups.orEmpty()

    /** Sugestão automática: mantém a cópia mais antiga (provável original) e marca as outras. */
    private fun autoSelection(groups: List<DuplicateGroup>): Set<String> =
        groups.flatMap { g -> g.files.drop(1).map { it.path } }.toSet()

    fun selectSuggested() {
        _selected.value = autoSelection(groups())
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun toggle(path: String) {
        val current = _selected.value
        if (path in current) {
            _selected.value = current - path
            return
        }
        // Nunca deixa marcar todas as cópias de um grupo
        val group = groups().firstOrNull { g -> g.files.any { it.path == path } } ?: return
        val othersUnselected = group.files.count { it.path != path && it.path !in current }
        if (othersUnselected == 0) {
            _message.value = "Mantenha pelo menos uma cópia"
            return
        }
        _selected.value = current + path
    }

    /** Bytes que serão liberados com a seleção atual. */
    fun selectedBytes(groups: List<DuplicateGroup>, selected: Set<String>): Long =
        groups.sumOf { g -> g.size * g.files.count { it.path in selected } }

    fun deleteSelected() {
        val toDelete = _selected.value
        if (toDelete.isEmpty()) return
        viewModelScope.launch {
            _deleting.value = true
            var freed = 0L
            var failures = 0
            val deleted = mutableSetOf<String>()
            val sizes = groups().flatMap { it.files }.associate { it.path to it.size }

            for (path in toDelete) {
                repo.delete(File(path))
                    .onSuccess {
                        deleted += path
                        freed += sizes[path] ?: 0L
                        db.hashes().delete(path)
                    }
                    .onFailure { failures++ }
            }

            // Tira os excluídos da lista; grupo com uma cópia só deixa de ser duplicado
            val remaining = groups()
                .map { g -> g.copy(files = g.files.filterNot { it.path in deleted }) }
                .filter { it.files.size >= 2 }
            _state.value = DupState.Ready(remaining)
            _selected.update { it - deleted }
            _deleting.value = false

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
