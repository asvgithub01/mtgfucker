package io.asv.mtgocr.ocrreader.feature.photos

import io.asv.mtgocr.ocrreader.core.designsystem.R as CommonR
import io.asv.mtgocr.ocrreader.feature.photos.R as PhotosR

import androidx.compose.foundation.clickable
import io.asv.mtgocr.ocrreader.core.designsystem.QueryDetailDialog
import io.asv.mtgocr.ocrreader.core.designsystem.QueryActions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun PhotoScreen(state: PhotoUiState, onRefresh: () -> Unit, onBack: () -> Unit,
    image: @Composable (String?) -> Unit) {
    var imports by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val section = if (imports) state.snapshot?.imports else state.snapshot?.evidence
    val selected = section?.records?.firstOrNull { it.id == selectedId }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            QueryActions(stringResource(CommonR.string.shell_home), stringResource(CommonR.string.shell_refresh),
                state.loading, onBack, onRefresh)
            Text(stringResource(PhotosR.string.photo_preview_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(PhotosR.string.photo_preview_notice))
            TabRow(if (imports) 1 else 0) {
                listOf(PhotosR.string.photo_preview_library, PhotosR.string.photo_preview_global).forEachIndexed { index, label ->
                    Tab(selected = imports == (index == 1), onClick = { imports = index == 1; selectedId = null },
                        text = { Text(stringResource(label)) })
                }
            }
            if (state.loading) CircularProgressIndicator()
            else if (section?.failed == true) Text(stringResource(CommonR.string.shell_read_error))
            else if (section?.records.isNullOrEmpty()) Text(stringResource(PhotosR.string.photo_preview_empty))
        }
        items(section?.records.orEmpty(), key = { it.id }) { record ->
            ListItem(headlineContent = { Text(record.title) }, modifier = Modifier.clickable { selectedId = record.id })
        }
    }
    if (selected != null) QueryDetailDialog(selected.title, stringResource(android.R.string.ok),
        onDismiss = { selectedId = null }) {
        image(selected.path)
        Text(selected.details.ifBlank { stringResource(PhotosR.string.photo_preview_no_metadata) })
    }
}
