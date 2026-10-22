package io.asv.mtgocr.ocrreader.feature.library

import io.asv.mtgocr.ocrreader.feature.library.R as LibraryR

import io.asv.mtgocr.ocrreader.core.designsystem.R as CommonR

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.asv.mtgocr.ocrreader.core.designsystem.LoadStatus
import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow
import io.asv.mtgocr.ocrreader.domain.library.*

/** Labels for destinations belong to the host, not to sibling feature dependencies. */
data class LibraryDestinationLabels(val catalog: String, val photos: String, val services: String)

@Composable
fun LibraryShellScreen(
    libraryName: String,
    state: LibraryShellUiState,
    onSection: (LibrarySection) -> Unit,
    onMembership: (String?) -> Unit,
    onQuery: (String) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    destinations: LibraryDestinationLabels,
    detail: @Composable (CollectionRow, () -> Unit) -> Unit,
    artwork: @Composable (String?, Modifier) -> Unit,
    onOpenLegacy: (() -> Unit)? = null,
    onOpenTriple: (() -> Unit)? = null,
    onOpenCatalog: (() -> Unit)? = null,
    onOpenPhotos: (() -> Unit)? = null,
    onOpenServices: (() -> Unit)? = null
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.rows.firstOrNull { it.id == selectedId }
    val grid = rememberLazyGridState()
    LaunchedEffect(state.options.section, state.options.membership, state.options.sort) { grid.scrollToItem(0) }
    LazyVerticalGrid(columns = GridCells.Fixed(2), state = grid,
        modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag("shell-grid"),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("shell-home")) { Text(stringResource(CommonR.string.shell_home)) }
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(CommonR.string.shell_refresh)) }
                }
                Text(libraryName, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(LibraryR.string.shell_read_only), style = MaterialTheme.typography.bodySmall)
                onOpenServices?.let { action ->
                    TextButton(onClick = action) { Text(destinations.services) }
                }
                onOpenPhotos?.let { action ->
                    TextButton(onClick = action) { Text(destinations.photos) }
                }
                onOpenCatalog?.let {
                    TextButton(onClick = it, modifier = Modifier.testTag("shell-open-catalog")) {
                        Text(destinations.catalog)
                    }
                }
                onOpenLegacy?.let {
                    TextButton(onClick = it, enabled = !state.loading && state.failure == null,
                        modifier = Modifier.testTag("shell-open-legacy")) {
                        Text(stringResource(LibraryR.string.shell_open_legacy))
                    }
                }
                onOpenTriple?.let {
                    TextButton(onClick = it, enabled = !state.loading && state.failure == null,
                        modifier = Modifier.testTag("shell-open-triple")) {
                        Text(stringResource(LibraryR.string.shell_open_triple))
                    }
                }
                TabRow(selectedTabIndex = state.options.section.ordinal) {
                    LibrarySection.values().forEach { section ->
                        val label = when (section) {
                            LibrarySection.CARDS -> LibraryR.string.shell_cards
                            LibrarySection.GROUPS -> CommonR.string.shell_groups
                            LibrarySection.DECKS -> CommonR.string.shell_decks
                        }
                        Tab(selected = state.options.section == section, onClick = { onSection(section) },
                            modifier = Modifier.testTag("tab-${section.name}"),
                            text = { Text(stringResource(label)) })
                    }
                }
                if (state.options.membership != null) {
                    TextButton(onClick = { onMembership(null) }, modifier = Modifier.testTag("back-memberships")) {
                        Text(stringResource(LibraryR.string.shell_all_memberships))
                    }
                    Text(state.options.membership, style = MaterialTheme.typography.titleMedium)
                }
                LoadStatus(state.loading,
                    state.failure?.let { stringResource(if (it == ShellReadFailure.LIBRARY_CHANGED)
                        LibraryR.string.shell_library_changed else CommonR.string.shell_read_error) },
                    stringResource(CommonR.string.launcher_retry), onRefresh)
            }
        }
        if (!state.loading && state.failure == null) {
            if (state.showMemberships) {
                if (state.memberships.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(LibraryR.string.shell_empty_groups)) }
                items(state.memberships, key = { "membership:${it.name}" }, span = { GridItemSpan(maxLineSpan) }) { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        supportingContent = { Text(stringResource(LibraryR.string.shell_group_count, group.copies, group.sideboard)) },
                        modifier = Modifier.testTag("membership-${group.name}").clickable { onMembership(group.name) }
                    )
                }
            } else {
                item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        OutlinedTextField(
                            value = state.options.query, onValueChange = onQuery, singleLine = true,
                            label = { Text(stringResource(LibraryR.string.shell_search)) },
                            modifier = Modifier.fillMaxWidth().testTag("shell-search")
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(LibraryR.string.shell_count, state.rows.size, state.copies))
                            TextButton(onClick = {
                                onSort(if (state.options.sort == LibrarySort.NEWEST) LibrarySort.NAME else LibrarySort.NEWEST)
                            }) {
                                Text(stringResource(if (state.options.sort == LibrarySort.NEWEST) LibraryR.string.shell_newest else LibraryR.string.shell_name))
                            }
                        }
                    }
                }
                if (state.rows.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(LibraryR.string.shell_empty_cards)) }
                items(state.rows, key = { "card:${it.id}" }) { card ->
                    Card(Modifier.fillMaxWidth().testTag("card-${card.id}").clickable { selectedId = card.id }) {
                        artwork(card.imageUrl, Modifier.fillMaxWidth().aspectRatio(0.716f))
                        Column(Modifier.padding(8.dp)) {
                            Text(card.name, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(CommonR.string.shell_row_count, card.quantity))
                            Text("${card.setCode} · ${card.language} · ${card.finish.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (selected != null && !state.loading && state.failure == null) {
        detail(selected) { selectedId = null }
    }
}
