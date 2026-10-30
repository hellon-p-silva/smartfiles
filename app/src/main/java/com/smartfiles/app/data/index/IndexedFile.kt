package com.smartfiles.app.data.index

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartfiles.app.data.FileItem
import com.smartfiles.app.data.FileKind
import java.io.File
import java.text.Normalizer

/**
 * Uma linha do índice = um arquivo ou pasta do armazenamento.
 * `nameNorm` é o nome sem acentos e em minúsculas, pra "relatorio" achar "Relatório".
 * `scanId` marca em qual varredura o item foi visto; o que sobra de varreduras antigas é apagado.
 */
@Entity(
    tableName = "files",
    indices = [Index("parentPath"), Index("lastModified"), Index("kind"), Index("scanId")],
)
data class IndexedFile(
    @PrimaryKey val path: String,
    val name: String,
    val nameNorm: String,
    val parentPath: String,
    val extension: String,
    val kind: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val scanId: Long,
) {
    companion object {
        fun from(file: File, scanId: Long): IndexedFile {
            val isDir = file.isDirectory
            val ext = file.extension.lowercase()
            return IndexedFile(
                path = file.absolutePath,
                name = file.name,
                nameNorm = normalizeText(file.name),
                parentPath = file.parent ?: "",
                extension = ext,
                kind = (if (isDir) FileKind.FOLDER else FileItem.kindOf(ext)).name,
                isDirectory = isDir,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                scanId = scanId,
            )
        }
    }
}

fun IndexedFile.toFile(): File = File(path)

fun IndexedFile.fileKind(): FileKind =
    runCatching { FileKind.valueOf(kind) }.getOrDefault(FileKind.OTHER)

private val DIACRITICS = "\\p{Mn}+".toRegex()

fun normalizeText(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

/** Escapa %, _ e \ para usar texto do usuário dentro de LIKE ... ESCAPE '\'. */
fun likeEscape(s: String): String =
    s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
