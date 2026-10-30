package com.smartfiles.app.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartfiles.app.data.FileItem
import com.smartfiles.app.ui.components.FileThumb
import com.smartfiles.app.util.formatDate
import com.smartfiles.app.util.formatSize
import com.smartfiles.app.util.openFile
import com.smartfiles.app.util.shareFile
import java.io.File

private sealed interface DialogState {
    data object NewFolder : DialogState
    data class Rename(val item: FileItem) : DialogState
    data class Delete(val item: FileItem) : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    vm: BrowserViewModel = viewModel(),
    onSearch: () -> Unit = {},
    onStorage: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DialogState?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    BackHandler(enabled = state.canGoUp) { vm.navigateUp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.currentDir == vm.root) "Armazenamento interno" else state.currentDir.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (state.canGoUp) {
                        IconButton(onClick = { vm.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = onStorage) {
                        Icon(Icons.Filled.PieChart, contentDescription = "Armazenamento")
                    }
                    IconButton(onClick = { vm.toggleHidden() }) {
                        Icon(
                            if (state.showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Mostrar ocultos",
                        )
                    }
                    IconButton(onClick = { dialog = DialogState.NewFolder }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Nova pasta")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Breadcrumbs(root = vm.root, current = state.currentDir, onNavigate = vm::open)
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (!state.isLoading && state.items.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.file.absolutePath }) { item ->
                        FileRow(
                            item = item,
                            onClick = {
                                if (item.isDirectory) vm.open(item.file) else openFile(context, item.file)
                            },
                            onShare = { shareFile(context, item.file) },
                            onRename = { dialog = DialogState.Rename(item) },
                            onDelete = { dialog = DialogState.Delete(item) },
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        DialogState.NewFolder -> NameDialog(
            title = "Nova pasta",
            initial = "",
            confirmLabel = "Criar",
            onConfirm = { vm.createFolder(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.Rename -> NameDialog(
            title = "Renomear",
            initial = d.item.name,
            confirmLabel = "Salvar",
            onConfirm = { vm.rename(d.item, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Excluir \"${d.item.name}\"?") },
            text = {
                Text(
                    if (d.item.isDirectory) "A pasta e tudo dentro dela serão apagados. Isso não pode ser desfeito."
                    else "O arquivo será apagado. Isso não pode ser desfeito."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.delete(d.item); dialog = null }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
        )
        null -> Unit
    }
}

@Composable
private fun Breadcrumbs(root: File, current: File, onNavigate: (File) -> Unit) {
    // Monta a cadeia raiz -> ... -> pasta atual
    val chain = remember(current) {
        generateSequence(current) { if (it == root) null else it.parentFile }.toList().reversed()
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(chain) { index, dir ->
            AssistChip(
                onClick = { onNavigate(dir) },
                label = { Text(if (dir == root) "Interno" else dir.name, maxLines = 1) },
            )
            if (index < chain.lastIndex) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FileRow(
    item: FileItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val subtitle = if (item.isDirectory) {
        val n = item.childCount ?: 0
        if (n == 1) "1 item" else "$n itens"
    } else {
        "${formatSize(context, item.size)} · ${formatDate(item.lastModified)}"
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { FileThumb(item.file, item.kind) },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opções")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!item.isDirectory) {
                        DropdownMenuItem(text = { Text("Compartilhar") }, onClick = { menuOpen = false; onShare() })
                    }
                    DropdownMenuItem(text = { Text("Renomear") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Excluir") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        },
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        Text("Pasta vazia", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
