package io.asv.mtgocr.ocrreader.feature.library

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
import io.asv.mtgocr.ocrreader.R
import io.asv.mtgocr.ocrreader.core.designsystem.CardArtwork
import io.asv.mtgocr.ocrreader.core.designsystem.LoadStatus
import io.asv.mtgocr.ocrreader.domain.library.*

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
    artwork: @Composable (String?, Modifier) -> Unit = { url, modifier -> CardArtwork(url, null, modifier) }
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
                    TextButton(onClick = onBack, modifier = Modifier.testTag("shell-home")) { Text(stringResource(R.string.shell_home)) }
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(R.string.shell_refresh)) }
                }
                Text(libraryName, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.shell_read_only), style = MaterialTheme.typography.bodySmall)
                TabRow(selectedTabIndex = state.options.section.ordinal) {
                    LibrarySection.values().forEach { section ->
                        val label = when (section) {
                            LibrarySection.CARDS -> R.string.shell_cards
                            LibrarySection.GROUPS -> R.string.shell_groups
                            LibrarySection.DECKS -> R.string.shell_decks
                        }
                        Tab(selected = state.options.section == section, onClick = { onSection(section) },
                            modifier = Modifier.testTag("tab-${section.name}"),
                            text = { Text(stringResource(label)) })
                    }
                }
                if (state.options.membership != null) {
                    TextButton(onClick = { onMembership(null) }, modifier = Modifier.testTag("back-memberships")) {
                        Text(stringResource(R.string.shell_all_memberships))
                    }
                    Text(state.options.membership, style = MaterialTheme.typography.titleMedium)
                }
                LoadStatus(state.loading,
                    state.failure?.let { stringResource(if (it == ShellReadFailure.LIBRARY_CHANGED)
                        R.string.shell_library_changed else R.string.shell_read_error) },
                    stringResource(R.string.launcher_retry), onRefresh)
            }
        }
        if (!state.loading && state.failure == null) {
            if (state.showMemberships) {
                if (state.memberships.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.shell_empty_groups)) }
                items(state.memberships, key = { "membership:${it.name}" }, span = { GridItemSpan(maxLineSpan) }) { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        supportingContent = { Text(stringResource(R.string.shell_group_count, group.copies, group.sideboard)) },
                        modifier = Modifier.testTag("membership-${group.name}").clickable { onMembership(group.name) }
                    )
                }
            } else {
                item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        OutlinedTextField(
                            value = state.options.query, onValueChange = onQuery, singleLine = true,
                            label = { Text(stringResource(R.string.shell_search)) },
                            modifier = Modifier.fillMaxWidth().testTag("shell-search")
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.shell_count, state.rows.size, state.copies))
                            TextButton(onClick = {
                                onSort(if (state.options.sort == LibrarySort.NEWEST) LibrarySort.NAME else LibrarySort.NEWEST)
                            }) {
                                Text(stringResource(if (state.options.sort == LibrarySort.NEWEST) R.string.shell_newest else R.string.shell_name))
                            }
                        }
                    }
                }
                if (state.rows.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.shell_empty_cards)) }
                items(state.rows, key = { "card:${it.id}" }) { card ->
                    Card(Modifier.fillMaxWidth().testTag("card-${card.id}").clickable { selectedId = card.id }) {
                        artwork(card.imageUrl, Modifier.fillMaxWidth().aspectRatio(0.716f))
                        Column(Modifier.padding(8.dp)) {
                            Text(card.name, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.shell_row_count, card.quantity))
                            Text("${card.setCode} · ${card.language} · ${card.finish.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (selected != null && !state.loading && state.failure == null) {
        AlertDialog(onDismissRequest = { selectedId = null },
            title = { Text(selected.name) },
            text = { Column {
                Text(selected.setName)
                Text(stringResource(R.string.shell_row_count, selected.quantity))
                Text("${selected.language} · ${selected.finish.orEmpty()} · ${selected.condition}")
                Text(stringResource(R.string.shell_read_only))
            } },
            confirmButton = { TextButton(onClick = { selectedId = null }) { Text(stringResource(R.string.shell_close)) } })
    }
}
