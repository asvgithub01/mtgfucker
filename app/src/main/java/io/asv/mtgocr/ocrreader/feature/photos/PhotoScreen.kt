package io.asv.mtgocr.ocrreader.feature.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.asv.mtgocr.ocrreader.R

@Composable
fun PhotoScreen(state: PhotoUiState, onRefresh: () -> Unit, onBack: () -> Unit,
    image: @Composable (String?) -> Unit) {
    var imports by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val section = if (imports) state.snapshot?.imports else state.snapshot?.evidence
    val selected = section?.records?.firstOrNull { it.id == selectedId }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row {
                TextButton(onClick = onBack) { Text(stringResource(R.string.shell_home)) }
                TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(R.string.shell_refresh)) }
            }
            Text(stringResource(R.string.photo_preview_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.photo_preview_notice))
            TabRow(if (imports) 1 else 0) {
                listOf(R.string.photo_preview_library, R.string.photo_preview_global).forEachIndexed { index, label ->
                    Tab(selected = imports == (index == 1), onClick = { imports = index == 1; selectedId = null },
                        text = { Text(stringResource(label)) })
                }
            }
            if (state.loading) CircularProgressIndicator()
            else if (section?.failed == true) Text(stringResource(R.string.shell_read_error))
            else if (section?.records.isNullOrEmpty()) Text(stringResource(R.string.photo_preview_empty))
        }
        items(section?.records.orEmpty(), key = { it.id }) { record ->
            ListItem(headlineContent = { Text(record.title) }, modifier = Modifier.clickable { selectedId = record.id })
        }
    }
    if (selected != null) AlertDialog(onDismissRequest = { selectedId = null },
        title = { Text(selected.title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            image(selected.path)
            Text(selected.details.ifBlank { stringResource(R.string.photo_preview_no_metadata) })
        } },
        confirmButton = { TextButton(onClick = { selectedId = null }) { Text(stringResource(android.R.string.ok)) } })
}
