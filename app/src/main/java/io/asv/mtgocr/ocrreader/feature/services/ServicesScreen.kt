package io.asv.mtgocr.ocrreader.feature.services

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.asv.mtgocr.ocrreader.R

@Composable
fun ServicesScreen(state: ServicesUiState, onRefresh: () -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("services-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row {
                TextButton(onClick = onBack) { Text(stringResource(R.string.shell_home)) }
                TextButton(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.testTag("services-refresh")) {
                    Text(stringResource(R.string.shell_refresh))
                }
            }
            Text(stringResource(R.string.services_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.services_notice))
            if (state.loading) CircularProgressIndicator()
            if (state.failed) Text(stringResource(R.string.shell_read_error))
        }
        val snapshot = state.snapshot.takeIf { !state.loading && !state.failed }
        if (snapshot != null) {
            item {
                Text(stringResource(R.string.services_premium, yesNo(snapshot.premium)))
                Text(stringResource(R.string.services_currency, snapshot.currency))
                Text(stringResource(R.string.services_auto, yesNo(snapshot.autoSyncPreference)))
                Text(stringResource(R.string.services_cloud_notice))
                Text(stringResource(R.string.services_files_notice))
            }
            items(snapshot.libraries, key = { it.id }) { library ->
                Card(Modifier.fillMaxWidth().testTag("service-library-${library.id}")) {
                    Column(Modifier.padding(12.dp)) {
                        Text(library.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.services_access, yesNo(library.available)))
                        Text(stringResource(R.string.services_primary, fileSize(library.primaryBytes)))
                        Text(stringResource(R.string.services_backup, fileSize(library.backupBytes)))
                    }
                }
            }
        }
    }
}
@Composable private fun yesNo(value: Boolean) = stringResource(if (value) R.string.services_yes else R.string.services_no)
@Composable private fun fileSize(bytes: Long?) = bytes?.let { pluralStringResource(R.plurals.services_bytes, if (it == 1L) 1 else 2, it) }
    ?: stringResource(R.string.services_missing)
