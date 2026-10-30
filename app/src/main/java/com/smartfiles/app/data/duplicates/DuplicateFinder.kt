package com.smartfiles.app.data.duplicates

import com.smartfiles.app.data.index.FileDao
import com.smartfiles.app.data.index.FileHash
import com.smartfiles.app.data.index.HashDao
import com.smartfiles.app.data.index.IndexedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Um conjunto de arquivos com conteúdo idêntico. `files[0]` é o mais antigo. */
data class DuplicateGroup(
    val hash: String,
    val size: Long,
    val files: List<IndexedFile>,
) {
    /** Espaço liberado se ficar só uma cópia. */
    val wasted: Long get() = size * (files.size - 1)
}

/**
 * Encontra duplicados em três filtros, do mais barato pro mais caro:
 * 1. mesmo tamanho (consulta no índice, instantâneo)
 * 2. hash rápido (início + fim do arquivo)
 * 3. hash completo SHA-256, com cache, só pra quem empatou no rápido
 */
class DuplicateFinder(
    private val files: FileDao,
    private val hashes: HashDao,
) {
    suspend fun find(
        minSize: Long = 1024,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val candidates = files.duplicateSizeCandidates(minSize)
        val total = candidates.size
        var done = 0
        val groups = mutableListOf<DuplicateGroup>()
        onProgress(0, total)

        for ((size, sameSize) in candidates.groupBy { it.size }) {
            // O índice pode estar um pouco atrasado: confere se o arquivo ainda está lá
            val existing = sameSize.filter { File(it.path).let { f -> f.isFile && f.length() == size } }

            if (existing.size >= 2) {
                val byQuick = existing.groupBy { runCatching { Hashing.quick(File(it.path)) }.getOrNull() }
                for ((quickHash, sameQuick) in byQuick) {
                    if (quickHash == null || sameQuick.size < 2) continue

                    val byFull =
                        if (size <= Hashing.QUICK_COVERS_ALL) mapOf(quickHash to sameQuick)
                        else sameQuick.groupBy { fullHashCached(it) }

                    for ((hash, same) in byFull) {
                        if (hash != null && same.size >= 2) {
                            groups += DuplicateGroup(hash, size, same.sortedBy { it.lastModified })
                        }
                    }
                }
            }
            done += sameSize.size
            onProgress(done, total)
        }
        groups.sortedByDescending { it.wasted }
    }

    private suspend fun fullHashCached(item: IndexedFile): String? {
        val file = File(item.path)
        val size = file.length()
        val modified = file.lastModified()
        hashes.get(item.path)?.let { cached ->
            if (cached.size == size && cached.lastModified == modified) return cached.hash
        }
        return runCatching { Hashing.full(file) }
            .onSuccess { hashes.upsert(FileHash(item.path, size, modified, it)) }
            .getOrNull()
    }
}
