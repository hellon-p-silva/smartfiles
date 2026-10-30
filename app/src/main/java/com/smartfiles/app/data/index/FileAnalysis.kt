package com.smartfiles.app.data.index

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Resultado da análise "inteligente" de uma imagem/PDF: categorias automáticas e rótulos do ML Kit.
 * `version` permite reprocessar tudo quando as regras melhorarem (basta subir CURRENT_VERSION).
 */
@Entity(tableName = "file_analysis")
data class FileAnalysis(
    @PrimaryKey val path: String,
    val size: Long,
    val lastModified: Long,
    /** ",print,comprovante," */
    val tags: String,
    /** ",cat,sky," (rótulos do ML Kit em inglês, minúsculos) */
    val labels: String,
    val version: Int,
    val processedAt: Long,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Dao
interface AnalysisDao {

    @Upsert
    suspend fun upsert(item: FileAnalysis)

    /** Tags de todos os arquivos ainda existentes, pra contar quantos há em cada categoria. */
    @Query("SELECT a.tags FROM file_analysis a JOIN files f ON f.path = a.path WHERE a.tags != ''")
    fun allTags(): Flow<List<String>>

    @Query("DELETE FROM file_analysis WHERE path NOT IN (SELECT path FROM files)")
    suspend fun deleteOrphans(): Int
}
