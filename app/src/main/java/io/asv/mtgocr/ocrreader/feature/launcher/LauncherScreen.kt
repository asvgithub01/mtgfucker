package io.asv.mtgocr.ocrreader.feature.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.asv.mtgocr.ocrreader.R
import io.asv.mtgocr.ocrreader.core.designsystem.CardArtwork
import io.asv.mtgocr.ocrreader.core.designsystem.LoadStatus

@Composable
fun LauncherScreen(
    state: LauncherUiState,
    onSelect: (String) -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onPreview: (() -> Unit)? = null,
    artwork: @Composable (String?, Modifier) -> Unit = { url, modifier ->
        CardArtwork(url, stringResource(R.string.launch_background), modifier)
    }
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xFF08130E))) {
        artwork(state.data?.backgroundUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0x62000000)))
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp)) {
                    Text(
                        stringResource(R.string.app_display_name),
                        Modifier.fillMaxWidth(), color = Color.White, fontSize = 42.sp,
                        fontFamily = FontFamily(Font(R.font.mtg_title)), textAlign = TextAlign.Center
                    )
                    Text(stringResource(R.string.launch_tagline), Modifier.fillMaxWidth(),
                        color = Color(0xFFE8DFCF), textAlign = TextAlign.Center)
                }
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val data = state.data
                        if (data != null && data.libraries.size > 1) {
                            Text(stringResource(R.string.choose_library), style = MaterialTheme.typography.titleSmall)
                            Box {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    enabled = !state.loading && !state.navigating,
                                    modifier = Modifier.fillMaxWidth().testTag("library-picker")
                                ) {
                                    Text(data.libraries.firstOrNull { it.id == data.selectedId }?.name.orEmpty())
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    data.libraries.forEach { library ->
                                        DropdownMenuItem(
                                            text = { Text(library.name) },
                                            onClick = { expanded = false; onSelect(library.id) }
                                        )
                                    }
                                }
                            }
                        }
                        LoadStatus(
                            loading = state.loading,
                            errorMessage = if (state.failed || data?.readable == false)
                                stringResource(R.string.launcher_read_error) else null,
                            retryLabel = stringResource(R.string.launcher_retry),
                            onRetry = onRetry
                        )
                        Button(onClick = onOpen, enabled = state.canOpen,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open-library")) {
                            Text(stringResource(R.string.mode_biblio))
                        }
                        if (onPreview != null) {
                            TextButton(onClick = onPreview, enabled = state.canOpen,
                                modifier = Modifier.fillMaxWidth().testTag("preview-library")) {
                                Text(stringResource(R.string.shell_preview))
                            }
                        }
                    }
                }
            }
        }
    }
}
