package com.smartfiles.app.data.index

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** Resultado da busca: o arquivo + texto do OCR e categorias automáticas (se houver). */
data class SearchHit(
    @Embedded val file: IndexedFile,
    val ocrText: String?,
    val tags: String?,
)

/** Resultado agregado: quanto cada tipo de arquivo ocupa. */
data class KindUsage(val kind: String, val total: Long, val count: Int)

@Dao
interface FileDao {

    @Upsert
    suspend fun upsert(items: List<IndexedFile>)

    /** Remove o que não apareceu na varredura atual (arquivos apagados fora do app). */
    @Query("DELETE FROM files WHERE scanId < :scanId")
    suspend fun deleteOlderThan(scanId: Long): Int

    /** Remove um item e, se for pasta, tudo dentro dela. */
    @Query("DELETE FROM files WHERE path = :path OR path LIKE :childrenPattern ESCAPE '\\'")
    suspend fun deleteTree(path: String, childrenPattern: String)

    @Query("SELECT COUNT(*) FROM files")
    fun count(): Flow<Int>

    /**
     * Busca principal. A consulta é montada pelo SearchQueryBuilder a partir do que o
     * QueryParser entendeu da frase (palavras, tipo, datas, categorias, origem, tamanho).
     */
    @RawQuery(observedEntities = [IndexedFile::class, FileText::class, FileAnalysis::class])
    fun search(query: SupportSQLiteQuery): Flow<List<SearchHit>>

    @Query(
        "SELECT kind, SUM(size) AS total, COUNT(*) AS count FROM files " +
            "WHERE isDirectory = 0 GROUP BY kind ORDER BY total DESC"
    )
    fun usageByKind(): Flow<List<KindUsage>>

    @Query("SELECT * FROM files WHERE isDirectory = 0 ORDER BY size DESC LIMIT :limit")
    fun largest(limit: Int = 30): Flow<List<IndexedFile>>

    /** Primeiro filtro do detector de duplicados: só arquivos cujo tamanho se repete. */
    @Query(
        """
        SELECT * FROM files
        WHERE isDirectory = 0 AND size >= :minSize
          AND size IN (
            SELECT size FROM files
            WHERE isDirectory = 0 AND size >= :minSize
            GROUP BY size HAVING COUNT(*) > 1
          )
        ORDER BY size DESC
        """
    )
    suspend fun duplicateSizeCandidates(minSize: Long): List<IndexedFile>
}
