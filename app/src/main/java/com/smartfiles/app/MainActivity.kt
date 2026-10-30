package com.smartfiles.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartfiles.app.data.index.IndexWorker
import com.smartfiles.app.ui.browser.BrowserScreen
import com.smartfiles.app.ui.browser.BrowserViewModel
import com.smartfiles.app.ui.permission.PermissionScreen
import com.smartfiles.app.ui.permission.hasAllFilesAccess
import com.smartfiles.app.ui.duplicates.DuplicatesScreen
import com.smartfiles.app.ui.search.SearchScreen
import com.smartfiles.app.ui.storage.StorageScreen
import com.smartfiles.app.ui.theme.SmartFilesTheme
import com.smartfiles.app.ui.whatsapp.WhatsAppScreen

private enum class Screen { BROWSER, SEARCH, STORAGE, DUPLICATES, WHATSAPP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFilesTheme {
                Surface {
                    val context = LocalContext.current
                    var granted by remember { mutableStateOf(hasAllFilesAccess()) }
                    var screen by rememberSaveable { mutableStateOf(Screen.BROWSER) }

                    // Rechecagem quando o usuário volta da tela de configurações
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasAllFilesAccess() }

                    // Com permissão, garante que o índice de busca está sendo mantido
                    LaunchedEffect(granted) { if (granted) IndexWorker.schedule(context) }

                    if (!granted) {
                        PermissionScreen()
                    } else {
                        val browserVm: BrowserViewModel = viewModel()
                        when (screen) {
                            Screen.BROWSER -> BrowserScreen(
                                vm = browserVm,
                                onSearch = { screen = Screen.SEARCH },
                                onStorage = { screen = Screen.STORAGE },
                            )
                            Screen.SEARCH -> SearchScreen(
                                onBack = { screen = Screen.BROWSER },
                                onOpenFolder = { dir ->
                                    browserVm.open(dir)
                                    screen = Screen.BROWSER
                                },
                            )
                            Screen.STORAGE -> StorageScreen(
                                onBack = { screen = Screen.BROWSER },
                                onOpenDuplicates = { screen = Screen.DUPLICATES },
                                onOpenWhatsApp = { screen = Screen.WHATSAPP },
                            )
                            Screen.DUPLICATES -> DuplicatesScreen(
                                onBack = { screen = Screen.STORAGE },
                            )
                            Screen.WHATSAPP -> WhatsAppScreen(
                                onBack = { screen = Screen.STORAGE },
                            )
                        }
                    }
                }
            }
        }
    }
}
