package com.smartfiles.app.data.index

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Cache do hash completo (SHA-256) de um arquivo. Fica numa tabela separada para a
 * varredura do índice não apagar os hashes. Só vale se tamanho e data ainda baterem.
 */
@Entity(tableName = "file_hashes")
data class FileHash(
    @PrimaryKey val path: String,
    val size: Long,
    val lastModified: Long,
    val hash: String,
)

@Dao
interface HashDao {
    @Query("SELECT * FROM file_hashes WHERE path = :path")
    suspend fun get(path: String): FileHash?

    @Upsert
    suspend fun upsert(hash: FileHash)

    @Query("DELETE FROM file_hashes WHERE path = :path")
    suspend fun delete(path: String)
}
