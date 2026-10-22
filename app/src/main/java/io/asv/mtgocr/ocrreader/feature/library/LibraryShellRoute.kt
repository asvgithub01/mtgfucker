package io.asv.mtgocr.ocrreader.feature.library

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

@Composable
fun LibraryShellRoute(
    libraryName: String,
    viewModel: LibraryShellViewModel,
    onOpenLegacy: (() -> Unit)? = null,
    onOpenTriple: (() -> Unit)? = null,
    onOpenCatalog: (() -> Unit)? = null,
    onOpenPhotos: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
            viewModel.observeChanges()
            awaitCancellation()
        }
    }
    LibraryShellScreen(libraryName, state, viewModel::section, viewModel::membership,
        viewModel::query, viewModel::sort, viewModel::refresh, onBack,
        onOpenLegacy = onOpenLegacy, onOpenTriple = onOpenTriple, onOpenCatalog = onOpenCatalog, onOpenPhotos = onOpenPhotos)
}
