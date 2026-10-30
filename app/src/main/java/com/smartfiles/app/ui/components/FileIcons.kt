package com.smartfiles.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.smartfiles.app.data.FileKind

@Composable
fun iconFor(kind: FileKind): Pair<ImageVector, Color> = when (kind) {
    FileKind.FOLDER -> Icons.Filled.Folder to MaterialTheme.colorScheme.primary
    FileKind.IMAGE -> Icons.Filled.Image to Color(0xFF16A34A)
    FileKind.VIDEO -> Icons.Filled.VideoFile to Color(0xFFDC2626)
    FileKind.AUDIO -> Icons.Filled.AudioFile to Color(0xFF9333EA)
    FileKind.PDF -> Icons.Filled.PictureAsPdf to Color(0xFFB91C1C)
    FileKind.DOCUMENT -> Icons.Filled.Description to Color(0xFF2563EB)
    FileKind.ARCHIVE -> Icons.Filled.FolderZip to Color(0xFFCA8A04)
    FileKind.APK -> Icons.Filled.Android to Color(0xFF65A30D)
    FileKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.outline
}

/** Rótulo curto usado nos chips de filtro. */
fun FileKind.label(): String = when (this) {
    FileKind.FOLDER -> "Pastas"
    FileKind.IMAGE -> "Imagens"
    FileKind.VIDEO -> "Vídeos"
    FileKind.AUDIO -> "Áudios"
    FileKind.PDF -> "PDFs"
    FileKind.DOCUMENT -> "Documentos"
    FileKind.ARCHIVE -> "Compactados"
    FileKind.APK -> "APKs"
    FileKind.OTHER -> "Outros"
}
