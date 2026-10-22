package io.asv.mtgocr.ocrreader.feature.launcher

import io.asv.mtgocr.ocrreader.feature.launcher.R as LauncherR

import io.asv.mtgocr.ocrreader.core.designsystem.R as CommonR

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.asv.mtgocr.ocrreader.core.designsystem.LoadStatus

/** Host supplies legacy branding without exposing app resources to this module. */
data class LauncherBranding(val appName: String, val tagline: String,
    val chooseLibrary: String, val openLibrary: String, val titleFont: FontFamily)

@Composable
fun LauncherScreen(
    state: LauncherUiState,
    onSelect: (String) -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    branding: LauncherBranding,
    artwork: @Composable (String?, Modifier) -> Unit,
    onPreview: (() -> Unit)? = null
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
                        branding.appName,
                        Modifier.fillMaxWidth(), color = Color.White, fontSize = 42.sp,
                        fontFamily = branding.titleFont, textAlign = TextAlign.Center
                    )
                    Text(branding.tagline, Modifier.fillMaxWidth(),
                        color = Color(0xFFE8DFCF), textAlign = TextAlign.Center)
                }
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val data = state.data
                        if (data != null && data.libraries.size > 1) {
                            Text(branding.chooseLibrary, style = MaterialTheme.typography.titleSmall)
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
                                stringResource(LauncherR.string.launcher_read_error) else null,
                            retryLabel = stringResource(CommonR.string.launcher_retry),
                            onRetry = onRetry
                        )
                        Button(onClick = onOpen, enabled = state.canOpen,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open-library")) {
                            Text(branding.openLibrary)
                        }
                        if (onPreview != null) {
                            TextButton(onClick = onPreview, enabled = state.canOpen,
                                modifier = Modifier.fillMaxWidth().testTag("preview-library")) {
                                Text(stringResource(LauncherR.string.shell_preview))
                            }
                        }
                    }
                }
            }
        }
    }
}
