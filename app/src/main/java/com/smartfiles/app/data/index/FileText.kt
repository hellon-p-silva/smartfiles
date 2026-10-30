package com.smartfiles.app.data.index

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Texto extraído por OCR de uma imagem ou PDF. Fica numa tabela separada (como os hashes)
 * pra varredura do índice não apagar. Arquivos sem texto também são gravados (texto vazio),
 * pra não serem processados de novo. Vale enquanto tamanho e data baterem com o índice.
 */
@Entity(tableName = "file_text")
data class FileText(
    @PrimaryKey val path: String,
    val size: Long,
    val lastModified: Long,
    val text: String,
    val textNorm: String,
    val processedAt: Long,
)

@Dao
interface TextDao {

    @Upsert
    suspend fun upsert(item: FileText)

    @Query("SELECT * FROM file_text WHERE path = :path")
    suspend fun get(path: String): FileText?

    /** Imagens e PDFs sem OCR ou sem análise atual (ou que mudaram desde então). Mais recentes primeiro. */
    @Query(
        """
        SELECT f.* FROM files f
        LEFT JOIN file_text t ON t.path = f.path
        LEFT JOIN file_analysis a ON a.path = f.path
        WHERE f.kind IN ('IMAGE', 'PDF')
          AND f.size BETWEEN :minSize AND :maxSize
          AND f.path NOT LIKE '%Stickers/%'
          AND (
            t.path IS NULL OR t.size != f.size OR t.lastModified != f.lastModified
            OR a.path IS NULL OR a.size != f.size OR a.lastModified != f.lastModified
            OR a.version < :version
          )
        ORDER BY f.lastModified DESC
        LIMIT :limit
        """
    )
    suspend fun pending(minSize: Long, maxSize: Long, version: Int, limit: Int): List<IndexedFile>

    @Query(
        """
        SELECT COUNT(*) FROM files f
        LEFT JOIN file_text t ON t.path = f.path
        LEFT JOIN file_analysis a ON a.path = f.path
        WHERE f.kind IN ('IMAGE', 'PDF')
          AND f.size BETWEEN :minSize AND :maxSize
          AND f.path NOT LIKE '%Stickers/%'
          AND (
            t.path IS NULL OR t.size != f.size OR t.lastModified != f.lastModified
            OR a.path IS NULL OR a.size != f.size OR a.lastModified != f.lastModified
            OR a.version < :version
          )
        """
    )
    suspend fun pendingCount(minSize: Long, maxSize: Long, version: Int): Int

    /** Remove textos de arquivos que já não existem no índice. */
    @Query("DELETE FROM file_text WHERE path NOT IN (SELECT path FROM files)")
    suspend fun deleteOrphans(): Int
}
