package com.smartfiles.app.data

import android.os.Environment
import com.smartfiles.app.data.index.FileIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Toda operação de disco passa por aqui, sempre fora da thread principal.
 * Cada ação feita aqui já atualiza o índice de busca, sem esperar a próxima varredura.
 */
class FileRepository(private val indexer: FileIndexer? = null) {

    val root: File = Environment.getExternalStorageDirectory()

    suspend fun list(dir: File, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val files = dir.listFiles() ?: throw IOException("Sem acesso a ${dir.name}")
        files
            .asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { FileItem.from(it) }
            .sortedWith(
                compareByDescending<FileItem> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
            .toList()
    }

    suspend fun createFolder(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            validateName(name)
            val target = File(parent, name.trim())
            if (target.exists()) throw IOException("Já existe um item com esse nome")
            if (!target.mkdir()) throw IOException("Não foi possível criar a pasta")
            indexer?.indexTree(target)
            target
        }
    }

    suspend fun rename(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            validateName(newName)
            val target = File(file.parentFile, newName.trim())
            if (target.exists()) throw IOException("Já existe um item com esse nome")
            if (!file.renameTo(target)) throw IOException("Não foi possível renomear")
            indexer?.removeTree(file.absolutePath)
            indexer?.indexTree(target)
            target
        }
    }

    suspend fun delete(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ok = file.deleteRecursively()
            indexer?.removeTree(file.absolutePath)
            if (!ok) {
                // Exclusão parcial: reindexa o que sobrou pra não deixar o índice mentindo
                if (file.exists()) indexer?.indexTree(file)
                throw IOException("Não foi possível excluir ${file.name}")
            }
        }
    }

    private fun validateName(name: String) {
        val n = name.trim()
        if (n.isEmpty()) throw IllegalArgumentException("O nome não pode ficar vazio")
        if (n.contains('/') || n == "." || n == "..") throw IllegalArgumentException("Nome inválido")
    }
}
