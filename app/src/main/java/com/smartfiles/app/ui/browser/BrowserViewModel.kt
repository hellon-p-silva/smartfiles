package com.smartfiles.app.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfiles.app.data.FileItem
import com.smartfiles.app.data.FileRepository
import com.smartfiles.app.data.index.AppDatabase
import com.smartfiles.app.data.index.FileIndexer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class BrowserState(
    val currentDir: File,
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val showHidden: Boolean = false,
    val canGoUp: Boolean = false,
    val message: String? = null,
)

class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FileRepository(FileIndexer(AppDatabase.get(app).files()))

    private val _state = MutableStateFlow(BrowserState(currentDir = repo.root))
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    val root: File get() = repo.root

    fun open(dir: File) {
        _state.update { it.copy(currentDir = dir, canGoUp = dir != repo.root) }
        refresh()
    }

    /** Retorna false se já estiver na raiz (aí o botão voltar fecha o app). */
    fun navigateUp(): Boolean {
        val current = _state.value.currentDir
        if (current == repo.root) return false
        open(current.parentFile ?: repo.root)
        return true
    }

    fun refresh() {
        val s = _state.value
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = runCatching { repo.list(s.currentDir, s.showHidden) }
            _state.update {
                it.copy(
                    items = result.getOrDefault(emptyList()),
                    isLoading = false,
                    message = result.exceptionOrNull()?.message ?: it.message,
                )
            }
        }
    }

    fun toggleHidden() {
        _state.update { it.copy(showHidden = !it.showHidden) }
        refresh()
    }

    fun createFolder(name: String) = runOp("Pasta criada") { repo.createFolder(_state.value.currentDir, name) }

    fun rename(item: FileItem, newName: String) = runOp("Renomeado") { repo.rename(item.file, newName) }

    fun delete(item: FileItem) = runOp("Excluído") { repo.delete(item.file) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun runOp(successMsg: String, op: suspend () -> Result<*>) {
        viewModelScope.launch {
            val result = op()
            _state.update { it.copy(message = result.exceptionOrNull()?.message ?: successMsg) }
            refresh()
        }
    }
}
