package io.asv.mtgocr.ocrreader.feature.services

import io.asv.mtgocr.ocrreader.core.designsystem.R as CommonR
import io.asv.mtgocr.ocrreader.feature.services.R as ServicesR

import io.asv.mtgocr.ocrreader.core.designsystem.QueryActions
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

@Composable
fun ServicesScreen(state: ServicesUiState, onRefresh: () -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("services-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            QueryActions(stringResource(CommonR.string.shell_home), stringResource(CommonR.string.shell_refresh),
                state.loading, onBack, onRefresh, refreshModifier = Modifier.testTag("services-refresh"))
            Text(stringResource(ServicesR.string.services_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(ServicesR.string.services_notice))
            if (state.loading) CircularProgressIndicator()
            if (state.failed) Text(stringResource(CommonR.string.shell_read_error))
        }
        val snapshot = state.snapshot.takeIf { !state.loading && !state.failed }
        if (snapshot != null) {
            item {
                Text(stringResource(ServicesR.string.services_premium, yesNo(snapshot.premium)))
                Text(stringResource(ServicesR.string.services_currency, snapshot.currency))
                Text(stringResource(ServicesR.string.services_auto, yesNo(snapshot.autoSyncPreference)))
                Text(stringResource(ServicesR.string.services_cloud_notice))
                Text(stringResource(ServicesR.string.services_files_notice))
            }
            items(snapshot.libraries, key = { it.id }) { library ->
                Card(Modifier.fillMaxWidth().testTag("service-library-${library.id}")) {
                    Column(Modifier.padding(12.dp)) {
                        Text(library.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(ServicesR.string.services_access, yesNo(library.available)))
                        Text(stringResource(ServicesR.string.services_primary, fileSize(library.primaryBytes)))
                        Text(stringResource(ServicesR.string.services_backup, fileSize(library.backupBytes)))
                    }
                }
            }
        }
    }
}
@Composable private fun yesNo(value: Boolean) = stringResource(if (value) ServicesR.string.services_yes else ServicesR.string.services_no)
@Composable private fun fileSize(bytes: Long?) = bytes?.let { pluralStringResource(ServicesR.plurals.services_bytes, if (it == 1L) 1 else 2, it) }
    ?: stringResource(ServicesR.string.services_missing)
