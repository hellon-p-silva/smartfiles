package com.smartfiles.app.ui.storage

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfiles.app.data.FileRepository
import com.smartfiles.app.data.index.AppDatabase
import com.smartfiles.app.data.index.FileIndexer
import com.smartfiles.app.data.index.IndexedFile
import com.smartfiles.app.data.index.KindUsage
import com.smartfiles.app.util.formatSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class StorageStats(val total: Long, val free: Long) {
    val used: Long get() = total - free
}

class StorageViewModel(private val app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val repo = FileRepository(FileIndexer(db.files()))

    private val _stats = MutableStateFlow(readStats())
    val stats: StateFlow<StorageStats> = _stats.asStateFlow()

    val usage: StateFlow<List<KindUsage>> = db.files().usageByKind()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val largest: StateFlow<List<IndexedFile>> = db.files().largest(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refreshStats() {
        _stats.value = readStats()
    }

    fun delete(item: IndexedFile) {
        viewModelScope.launch {
            val result = repo.delete(File(item.path))
            db.hashes().delete(item.path)
            _message.value = result.exceptionOrNull()?.message
                ?: "${formatSize(app, item.size)} liberados"
            refreshStats()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun readStats(): StorageStats {
        val fs = StatFs(Environment.getExternalStorageDirectory().path)
        return StorageStats(total = fs.totalBytes, free = fs.availableBytes)
    }
}
