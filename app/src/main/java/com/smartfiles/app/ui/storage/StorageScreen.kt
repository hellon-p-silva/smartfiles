package com.smartfiles.app.ui.storage

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartfiles.app.data.FileKind
import com.smartfiles.app.data.index.IndexedFile
import com.smartfiles.app.data.index.fileKind
import com.smartfiles.app.data.index.toFile
import com.smartfiles.app.ui.components.FileThumb
import com.smartfiles.app.ui.components.iconFor
import com.smartfiles.app.ui.components.label
import com.smartfiles.app.util.formatSize
import com.smartfiles.app.util.openFile

private data class Segment(val label: String, val bytes: Long, val count: Int?, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    vm: StorageViewModel = viewModel(),
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val largest by vm.largest.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var toDelete by remember { mutableStateOf<IndexedFile?>(null) }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.refreshStats() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    // Monta as fatias da barra: tipos indexados + "apps e sistema" (o que o índice não vê)
    val segments = buildList {
        usage.forEach { u ->
            val kind = runCatching { FileKind.valueOf(u.kind) }.getOrDefault(FileKind.OTHER)
            add(Segment(kind.label(), u.total, u.count, iconFor(kind).second))
        }
        val indexed = usage.sumOf { it.total }
        val system = (stats.used - indexed).coerceAtLeast(0)
        if (system > 0) add(Segment("Apps e sistema", system, null, MaterialTheme.colorScheme.outlineVariant))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Armazenamento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${formatSize(context, stats.used)} usados",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "de ${formatSize(context, stats.total)} · ${formatSize(context, stats.free)} livres",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(16.dp))
                        StackedBar(segments = segments, total = stats.total)
                        Spacer(Modifier.height(16.dp))
                        segments.forEach { LegendRow(it, stats.total) }
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onOpenDuplicates)
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text("Arquivos duplicados") },
                        supportingContent = { Text("Encontre cópias idênticas e libere espaço") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    )
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onOpenWhatsApp)
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Filled.Forum, contentDescription = null, tint = Color(0xFF25D366))
                        },
                        headlineContent = { Text("Limpeza do WhatsApp") },
                        supportingContent = { Text("Fotos, vídeos e áudios antigos recebidos nas conversas") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    )
                }
            }

            item {
                Text(
                    "Maiores arquivos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
            items(largest, key = { it.path }) { item ->
                LargeFileRow(
                    item = item,
                    onClick = { openFile(context, item.toFile()) },
                    onDelete = { toDelete = item },
                )
            }
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Excluir \"${item.name}\"?") },
            text = { Text("Libera ${formatSize(context, item.size)}. Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(item); toDelete = null }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun StackedBar(segments: List<Segment>, total: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (total <= 0) return@Row
        segments.forEach { seg ->
            val fraction = seg.bytes.toFloat() / total
            if (fraction > 0.002f) {
                Box(Modifier.weight(fraction).fillMaxHeight().background(seg.color))
            }
        }
        // Espaço livre: o resto da barra
        val usedFraction = segments.sumOf { it.bytes }.toFloat() / total
        val free = 1f - usedFraction
        if (free > 0.002f) Spacer(Modifier.weight(free))
    }
}

@Composable
private fun LegendRow(seg: Segment, total: Long) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(seg.color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(seg.label, style = MaterialTheme.typography.bodyMedium)
            seg.count?.let {
                Text(
                    if (it == 1) "1 arquivo" else "$it arquivos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        val pct = if (total > 0) seg.bytes * 100.0 / total else 0.0
        Text(
            "${formatSize(context, seg.bytes)} · ${"%.1f".format(pct)}%",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LargeFileRow(item: IndexedFile, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    val location = item.parentPath.removePrefix(rootPath).ifEmpty { "/" }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { FileThumb(item.toFile(), item.fileKind()) },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "${formatSize(context, item.size)} · $location",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir")
            }
        },
    )
}
