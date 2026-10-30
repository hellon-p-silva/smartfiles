package com.smartfiles.app.data.index

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Varre o armazenamento e mantém a tabela `files` sincronizada.
 * - fullScan: varredura completa (roda no IndexWorker)
 * - indexTree / removeTree: atualizações pontuais após ações feitas no próprio app
 */
class FileIndexer(
    private val dao: FileDao,
    private val root: File = Environment.getExternalStorageDirectory(),
) {
    // Pastas que o Android 11+ bloqueia ou que só têm lixo de outros apps
    private val skipPaths = setOf(
        File(root, "Android/data").absolutePath,
        File(root, "Android/obb").absolutePath,
    )

    suspend fun fullScan(onProgress: suspend (Int) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val scanId = System.currentTimeMillis()
        val batch = ArrayList<IndexedFile>(BATCH_SIZE)
        var count = 0

        walk(root) { file ->
            batch += IndexedFile.from(file, scanId)
            count++
            if (batch.size >= BATCH_SIZE) {
                dao.upsert(batch.toList())
                batch.clear()
                onProgress(count)
            }
        }
        if (batch.isNotEmpty()) dao.upsert(batch.toList())

        // Só limpa o que sumiu depois que a varredura terminou inteira
        dao.deleteOlderThan(scanId)
        onProgress(count)
        count
    }

    suspend fun indexTree(file: File) = withContext(Dispatchers.IO) {
        val scanId = System.currentTimeMillis()
        val batch = ArrayList<IndexedFile>()
        batch += IndexedFile.from(file, scanId)
        if (file.isDirectory) walk(file) { batch += IndexedFile.from(it, scanId) }
        batch.chunked(BATCH_SIZE).forEach { dao.upsert(it) }
    }

    suspend fun removeTree(path: String) = withContext(Dispatchers.IO) {
        dao.deleteTree(path, likeEscape(path) + "/%")
    }

    private suspend fun walk(start: File, visit: suspend (File) -> Unit) {
        val stack = ArrayDeque<File>().apply { add(start) }
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive() // permite cancelar no meio
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.name.startsWith(".")) continue // ocultos (.thumbnails, .trash...)
                if (child.absolutePath in skipPaths) continue
                visit(child)
                if (child.isDirectory) stack.add(child)
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 500
    }
}
