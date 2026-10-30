package com.smartfiles.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smartfiles.app.data.FileKind
import java.io.File

/** Miniatura para imagens e vídeos (primeiro quadro); ícone colorido para o resto. */
@Composable
fun FileThumb(file: File, kind: FileKind, size: Dp = 40.dp) {
    if (kind == FileKind.IMAGE || kind == FileKind.VIDEO) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        val (icon, tint) = iconFor(kind)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}
