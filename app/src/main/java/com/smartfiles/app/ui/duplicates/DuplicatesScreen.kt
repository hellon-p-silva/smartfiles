package com.smartfiles.app.ui.duplicates

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartfiles.app.data.duplicates.DuplicateGroup
import com.smartfiles.app.data.index.IndexedFile
import com.smartfiles.app.data.index.fileKind
import com.smartfiles.app.data.index.toFile
import com.smartfiles.app.ui.components.FileThumb
import com.smartfiles.app.util.formatDate
import com.smartfiles.app.util.formatSize
import com.smartfiles.app.util.openFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(onBack: () -> Unit, vm: DuplicatesViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val deleting by vm.deleting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    val groups = (state as? DupState.Ready)?.groups.orEmpty()
    val selectedBytes = vm.selectedBytes(groups, selected)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicados") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = vm::scan, enabled = state is DupState.Ready && !deleting) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Procurar de novo")
                    }
                    IconButton(onClick = { menuOpen = true }, enabled = groups.isNotEmpty()) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Opções")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Marcar sugeridos") },
                            onClick = { menuOpen = false; vm.selectSuggested() },
                        )
                        DropdownMenuItem(
                            text = { Text("Desmarcar tudo") },
                            onClick = { menuOpen = false; vm.clearSelection() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (groups.isNotEmpty()) {
                BottomAppBar {
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("${selected.size} selecionados", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Libera ${formatSize(context, selectedBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Button(
                        onClick = { confirm = true },
                        enabled = selected.isNotEmpty() && !deleting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(end = 16.dp),
                    ) { Text(if (deleting) "Excluindo…" else "Excluir") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is DupState.Scanning -> ScanningView(s)
                is DupState.Ready -> if (s.groups.isEmpty()) {
                    NoDuplicatesView()
                } else {
                    val totalWasted = s.groups.sumOf { it.wasted }
                    Text(
                        "${s.groups.size} grupos · até ${formatSize(context, totalWasted)} pra liberar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.groups, key = { it.hash }) { group ->
                            GroupCard(group, selected, onToggle = vm::toggle)
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Excluir ${selected.size} cópias?") },
            text = {
                Text(
                    "Libera ${formatSize(context, selectedBytes)}. Em cada grupo pelo menos uma cópia " +
                        "é mantida. Isso não pode ser desfeito."
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
private fun ScanningView(s: DupState.Scanning) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Procurando duplicados…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (s.total > 0) {
            LinearProgressIndicator(
                progress = { s.done.toFloat() / s.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${s.done} de ${s.total} arquivos com tamanho repetido",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NoDuplicatesView() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF16A34A),
        )
        Spacer(Modifier.height(12.dp))
        Text("Nenhum duplicado encontrado", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Se você baixou arquivos agora há pouco, reindexe na tela de busca e procure de novo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GroupCard(group: DuplicateGroup, selected: Set<String>, onToggle: (String) -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Text(
            "${group.files.size} cópias · ${formatSize(context, group.size)} cada · " +
                "libera ${formatSize(context, group.wasted)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        group.files.forEachIndexed { index, file ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            DuplicateRow(
                item = file,
                isOldest = index == 0,
                checked = file.path in selected,
                onToggle = { onToggle(file.path) },
            )
        }
    }
}

@Composable
private fun DuplicateRow(item: IndexedFile, isOldest: Boolean, checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    val location = item.parentPath.removePrefix(rootPath).ifEmpty { "/" }
    val overline: (@Composable () -> Unit)? =
        if (isOldest) {
            { Text("Mais antigo", color = MaterialTheme.colorScheme.primary) }
        } else null
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { openFile(context, item.toFile()) },
        leadingContent = { FileThumb(item.toFile(), item.fileKind(), size = 48.dp) },
        overlineContent = overline,
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "$location · ${formatDate(item.lastModified)}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) },
    )
}
