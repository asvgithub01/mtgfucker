package io.asv.mtgocr.ocrreader.feature.catalog

import io.asv.mtgocr.ocrreader.feature.catalog.R as CatalogR
import io.asv.mtgocr.ocrreader.core.designsystem.R as CommonR

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.asv.mtgocr.ocrreader.core.designsystem.LoadStatus

/** Stateless read-only catalog. Selecting a set never selects an owned printing. */
@Composable
fun SetCatalogScreen(state: SetCatalogUiState, onQuery: (String) -> Unit,
    onSelect: (String) -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("set-catalog"),
        contentPadding = PaddingValues(16.dp)) {
        item(key = "header") {
            TextButton(onClick = onBack, modifier = Modifier.testTag("catalog-back")) {
                Text(stringResource(CatalogR.string.catalog_back))
            }
            Text(stringResource(CatalogR.string.catalog_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(CatalogR.string.catalog_notice), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = state.query, onValueChange = onQuery, singleLine = true,
                label = { Text(stringResource(CatalogR.string.catalog_search)) },
                modifier = Modifier.fillMaxWidth().testTag("catalog-search"))
            LoadStatus(state.loading, if (state.failed) stringResource(CatalogR.string.catalog_error) else null,
                stringResource(CommonR.string.launcher_retry), onRetry,
                modifier = Modifier.testTag(if (state.loading) "catalog-loading"
                    else if (state.failed) "catalog-failed" else "catalog-ready"))
        }
        if (!state.loading && !state.failed) {
            item(key = "count") { Text(pluralStringResource(CatalogR.plurals.catalog_count, state.sets.size, state.sets.size)) }
            if (state.sets.isEmpty()) item(key = "empty") { Text(stringResource(CatalogR.string.catalog_empty)) }
            items(state.sets, key = { "set:${it.code}" }) { set ->
                ListItem(headlineContent = { Text(set.name) },
                    supportingContent = { Text("${set.code} · ${set.releaseDate}") },
                    modifier = Modifier.testTag("set-${set.code}").clickable { onSelect(set.code) })
            }
        }
    }
    state.selected?.takeIf { !state.loading && !state.failed }?.let { set ->
        AlertDialog(onDismissRequest = onDismiss, title = { Text(set.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(set.code)
                Text(stringResource(CatalogR.string.catalog_release, set.releaseDate))
                Text(pluralStringResource(CatalogR.plurals.catalog_cards, set.cardCount, set.cardCount))
                Text(stringResource(CatalogR.string.catalog_notice))
            } },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.shell_close)) } })
    }
}
