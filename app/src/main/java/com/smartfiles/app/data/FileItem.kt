package com.smartfiles.app.data

import java.io.File

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, ARCHIVE, APK, OTHER }

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int?,
    val kind: FileKind,
) {
    val extension: String get() = file.extension.lowercase()

    companion object {
        fun from(file: File): FileItem {
            val isDir = file.isDirectory
            return FileItem(
                file = file,
                name = file.name,
                isDirectory = isDir,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                childCount = if (isDir) file.list()?.size else null,
                kind = if (isDir) FileKind.FOLDER else kindOf(file.extension.lowercase()),
            )
        }

        fun kindOf(ext: String): FileKind = when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> FileKind.IMAGE
            "mp4", "mkv", "mov", "avi", "webm", "3gp" -> FileKind.VIDEO
            "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac" -> FileKind.AUDIO
            "pdf" -> FileKind.PDF
            "doc", "docx", "txt", "odt", "rtf", "xls", "xlsx", "csv", "ppt", "pptx", "md" -> FileKind.DOCUMENT
            "zip", "rar", "7z", "tar", "gz" -> FileKind.ARCHIVE
            "apk", "apks", "xapk" -> FileKind.APK
            else -> FileKind.OTHER
        }
    }
}
