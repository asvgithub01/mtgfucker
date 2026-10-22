package io.asv.mtgocr.ocrreader.feature.launcher

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

@Composable
fun LauncherRoute(viewModel: LauncherViewModel, branding: LauncherBranding,
    artwork: @Composable (String?, Modifier) -> Unit, onPreview: ((String, String) -> Unit)? = null, onOpen: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
            awaitCancellation()
        }
    }
    LauncherScreen(state, viewModel::select, onOpen, viewModel::refresh, branding, artwork,
        onPreview = onPreview?.let { callback -> {
            if (state.canOpen) {
                state.data?.let { data ->
                    data.libraries.firstOrNull { it.id == data.selectedId }?.let { callback(it.id, it.name) }
                }
            }
        } })
}
