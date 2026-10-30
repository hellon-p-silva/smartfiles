package com.smartfiles.app.ui.whatsapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.smartfiles.app.data.FileItem
import com.smartfiles.app.data.FileKind
import com.smartfiles.app.data.whatsapp.WaCategory
import com.smartfiles.app.data.whatsapp.WaFile
import com.smartfiles.app.util.formatDate
import com.smartfiles.app.util.formatSize
import com.smartfiles.app.util.openFile
import java.io.File

private val WA_GREEN = Color(0xFF25D366)

private fun WaCategory.icon(): ImageVector = when (this) {
    WaCategory.IMAGES -> Icons.Filled.Image
    WaCategory.VIDEOS -> Icons.Filled.VideoFile
    WaCategory.VOICE_NOTES -> Icons.Filled.Mic
    WaCategory.AUDIO -> Icons.Filled.AudioFile
    WaCategory.DOCUMENTS -> Icons.Filled.Description
    WaCategory.STICKERS -> Icons.Filled.EmojiEmotions
    WaCategory.GIFS -> Icons.Filled.Gif
    WaCategory.VIDEO_NOTES -> Icons.Filled.Videocam
    WaCategory.PROFILE_PHOTOS -> Icons.Filled.AccountCircle
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppScreen(onBack: () -> Unit, vm: WhatsAppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val category = state.category

    BackHandler { if (category != null) vm.closeCategory() else onBack() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.label ?: "Limpeza do WhatsApp") },
                navigationIcon = {
                    IconButton(onClick = { if (category != null) vm.closeCategory() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (category == null) {
                        IconButton(onClick = vm::load, enabled = !state.loading) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                        }
                    } else {
                        IconButton(onClick = vm::toggleSelectAll, enabled = state.inCategory.isNotEmpty()) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Selecionar todos")
                        }
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            WaSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    onClick = { sortMenu = false; vm.setSort(sort) },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (category != null && state.selected.isNotEmpty()) {
                BottomAppBar {
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("${state.selected.size} selecionados", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Libera ${formatSize(context, state.selectedBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Button(
                        onClick = { confirm = true },
                        enabled = !state.deleting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(end = 16.dp),
                    ) { Text(if (state.deleting) "Excluindo…" else "Excluir") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingView()
                !state.installed -> NotFoundView()
                else -> {
                    Filters(state.age, state.includeSent, onAge = vm::setAge, onToggleSent = vm::toggleSent)
                    if (category == null) {
                        Overview(state, onOpen = vm::openCategory)
                    } else if (state.inCategory.isEmpty()) {
                        EmptyCategory()
                    } else if (category.visual) {
                        MediaGrid(state.inCategory, state.selected, onToggle = vm::toggle)
                    } else {
                        FileList(state.inCategory, state.selected, onToggle = vm::toggle)
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Excluir ${state.selected.size} arquivos?") },
            text = {
                Text(
                    "Libera ${formatSize(context, state.selectedBytes)}. Os arquivos somem do celular, " +
                        "mas as conversas continuam no WhatsApp. Isso não pode ser desfeito."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.deleteSelected() }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Filters(age: AgeFilter, includeSent: Boolean, onAge: (AgeFilter) -> Unit, onToggleSent: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(AgeFilter.entries) { a ->
            FilterChip(selected = age == a, onClick = { onAge(a) }, label = { Text(a.label) })
        }
        item {
            FilterChip(selected = includeSent, onClick = onToggleSent, label = { Text("Incluir enviados") })
        }
    }
}

@Composable
private fun Overview(state: WaState, onOpen: (WaCategory) -> Unit) {
    val context = LocalContext.current
    val totalBytes = state.summaries.sumOf { it.bytes }
    val totalCount = state.summaries.sumOf { it.count }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        formatSize(context, totalBytes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = WA_GREEN,
                    )
                    Text(
                        "em $totalCount arquivos" +
                            if (state.age != AgeFilter.ALL) " com ${state.age.label.lowercase()}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        if (state.summaries.isEmpty()) {
            item { EmptyCategory() }
        }
        items(state.summaries, key = { it.category }) { s ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(s.category) }) {
                ListItem(
                    leadingContent = { Icon(s.category.icon(), contentDescription = null, tint = WA_GREEN) },
                    headlineContent = { Text(s.category.label) },
                    supportingContent = { Text(if (s.count == 1) "1 arquivo" else "${s.count} arquivos") },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatSize(context, s.bytes), style = MaterialTheme.typography.titleSmall)
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    },
                )
            }
        }
        item {
            Text(
                "Dica: comece por \"+3 meses\". Áudios de voz e vídeos antigos costumam ser o que mais ocupa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(files: List<WaFile>, selected: Set<String>, onToggle: (String) -> Unit) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(files, key = { it.path }) { f ->
            val isSelected = f.path in selected
            val isVideo = FileItem.kindOf(File(f.path).extension.lowercase()) == FileKind.VIDEO
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    // Toque marca/desmarca; toque longo abre o arquivo
                    .combinedClickable(
                        onClick = { onToggle(f.path) },
                        onLongClick = { openFile(context, File(f.path)) },
                    )
            ) {
                AsyncImage(
                    model = File(f.path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isVideo) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                    )
                }
                if (isSelected) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                }
                Icon(
                    if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selecionado" else null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
                )
                Text(
                    formatSize(context, f.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(topEnd = 6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun FileList(files: List<WaFile>, selected: Set<String>, onToggle: (String) -> Unit) {
    val context = LocalContext.current
    LazyColumn {
        items(files, key = { it.path }) { f ->
            ListItem(
                modifier = Modifier.clickable { openFile(context, File(f.path)) },
                leadingContent = { Icon(f.category.icon(), contentDescription = null, tint = WA_GREEN) },
                headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        "${formatSize(context, f.size)} · ${formatDate(f.lastModified)}" +
                            if (f.sent) " · enviado" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Checkbox(checked = f.path in selected, onCheckedChange = { onToggle(f.path) })
                },
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Lendo as mídias do WhatsApp…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun NotFoundView() {
    CenteredMessage(
        title = "WhatsApp não encontrado",
        body = "Não achamos as pastas de mídia do WhatsApp nem do WhatsApp Business neste celular.",
    )
}

@Composable
private fun EmptyCategory() {
    CenteredMessage(title = "Nada por aqui", body = "Nenhum arquivo com esses filtros.")
}

@Composable
private fun CenteredMessage(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}
