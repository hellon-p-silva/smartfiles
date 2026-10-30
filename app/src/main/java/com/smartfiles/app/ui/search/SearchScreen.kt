package com.smartfiles.app.ui.search

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartfiles.app.data.FileKind
import com.smartfiles.app.data.index.SearchHit
import com.smartfiles.app.data.smart.SmartTag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.SuggestionChip
import com.smartfiles.app.data.index.normalizeText
import com.smartfiles.app.data.index.fileKind
import com.smartfiles.app.data.index.toFile
import com.smartfiles.app.ui.components.FileThumb
import com.smartfiles.app.ui.components.label
import com.smartfiles.app.util.formatDate
import com.smartfiles.app.util.formatSize
import com.smartfiles.app.util.openFile
import java.io.File

private val KIND_FILTERS = listOf(
    FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO, FileKind.PDF,
    FileKind.DOCUMENT, FileKind.ARCHIVE, FileKind.APK, FileKind.FOLDER,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFolder: (File) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val filters by vm.filters.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val indexedCount by vm.indexedCount.collectAsStateWithLifecycle()
    val progress by vm.indexingProgress.collectAsStateWithLifecycle()
    val ocr by vm.ocrProgress.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val tagCounts by vm.tagCounts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = remember { FocusRequester() }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = {
                    TextField(
                        value = filters.query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text("Ex.: comprovantes de pix do mês passado", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        trailingIcon = {
                            if (filters.query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Limpar")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                actions = {
                    IconButton(onClick = vm::reindex, enabled = progress == null) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reindexar")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            IndexStatus(progress = progress, indexedCount = indexedCount, ocr = ocr)

            // O que o app entendeu da frase
            if (filters.query.isNotBlank() && plan.understood.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        Text(
                            "Entendi:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    items(plan.understood) { label ->
                        AssistChip(onClick = {}, label = { Text(label) })
                    }
                }
            }

            // Categorias automáticas (só as que têm arquivos)
            val visibleTags = SmartTag.entries.filter { (tagCounts[it] ?: 0) > 0 || filters.tag == it }
            if (visibleTags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleTags) { tag ->
                        FilterChip(
                            selected = filters.tag == tag,
                            onClick = { vm.setTag(tag) },
                            label = { Text("${tag.label} · ${tagCounts[tag] ?: 0}") },
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(KIND_FILTERS) { kind ->
                    FilterChip(
                        selected = filters.kind == kind,
                        onClick = { vm.setKind(kind) },
                        label = { Text(kind.label()) },
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(DateFilter.entries) { date ->
                    FilterChip(
                        selected = filters.date == date,
                        onClick = { vm.setDate(date) },
                        label = { Text(date.label) },
                    )
                }
            }

            if (filters.isDefault) {
                Text(
                    "Experimente:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(EXAMPLES) { example ->
                        SuggestionChip(onClick = { vm.useExample(example) }, label = { Text(example) })
                    }
                }
            }

            if (filters.isDefault && results.isNotEmpty()) {
                Text(
                    "Recentes",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }

            if (results.isEmpty()) {
                EmptyResults(indexing = progress != null || indexedCount == 0)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.file.path }) { hit ->
                        val item = hit.file
                        ResultRow(
                            hit = hit,
                            keywords = plan.keywords,
                            onClick = {
                                if (item.isDirectory) onOpenFolder(item.toFile())
                                else openFile(context, item.toFile())
                            },
                            onShowInFolder = { onOpenFolder(File(item.parentPath)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexStatus(progress: Int?, indexedCount: Int, ocr: Pair<Int, Int>?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        val text = when {
            progress != null -> "Indexando… ${"%,d".format(progress).replace(',', '.')} itens"
            else -> "${"%,d".format(indexedCount).replace(',', '.')} itens indexados"
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        if (progress != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (ocr != null) {
            val (done, total) = ocr
            Spacer(Modifier.height(4.dp))
            Text(
                "Analisando imagens e PDFs… $done de $total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResultRow(hit: SearchHit, keywords: List<String>, onClick: () -> Unit, onShowInFolder: () -> Unit) {
    val item = hit.file
    val context = LocalContext.current
    val snippet = remember(hit.ocrText, keywords) { snippetFor(item.name, hit.ocrText, keywords) }
    val tagLabels = remember(hit.tags) { SmartTag.parse(hit.tags).joinToString(" · ") { it.label } }
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    val location = item.parentPath.removePrefix(rootPath).ifEmpty { "/" }
    val details = (if (item.isDirectory) location
    else "${formatSize(context, item.size)} · ${formatDate(item.lastModified)} · $location")

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { FileThumb(item.toFile(), item.fileKind()) },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(details, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tagLabels.isNotEmpty()) {
                    Text(
                        tagLabels,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (snippet != null) {
                    val (before, match, after) = snippet
                    Text(
                        buildAnnotatedString {
                            append("“…$before")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                append(match)
                            }
                            append("$after…”")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onShowInFolder) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "Mostrar na pasta")
            }
        },
    )
}

@Composable
private fun EmptyResults(indexing: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (indexing) "Indexando seus arquivos… os resultados aparecem em instantes"
            else "Nada encontrado",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private val EXAMPLES = listOf(
    "comprovantes de pix do mês passado",
    "prints de hoje",
    "fotos do gato",
    "boletos de luz",
    "vídeos grandes do whatsapp",
    "pdf contrato",
)

/**
 * Pega a primeira palavra da busca que não está no nome mas aparece no texto do OCR
 * e devolve o trecho em volta dela: (antes, palavra, depois). Usa os índices do texto
 * normalizado, que tem o mesmo tamanho do original pra texto comum em português.
 */
private fun snippetFor(name: String, ocrText: String?, keywords: List<String>): Triple<String, String, String>? {
    if (keywords.isEmpty() || ocrText.isNullOrEmpty()) return null
    val nameNorm = normalizeText(name)
    val norm = normalizeText(ocrText)
    if (norm.length != ocrText.length) return null
    val word = keywords.firstOrNull { it !in nameNorm && it in norm } ?: return null
    val idx = norm.indexOf(word)
    val start = (idx - 30).coerceAtLeast(0)
    val end = (idx + word.length + 50).coerceAtMost(ocrText.length)
    return Triple(
        ocrText.substring(start, idx),
        ocrText.substring(idx, idx + word.length),
        ocrText.substring(idx + word.length, end),
    )
}
