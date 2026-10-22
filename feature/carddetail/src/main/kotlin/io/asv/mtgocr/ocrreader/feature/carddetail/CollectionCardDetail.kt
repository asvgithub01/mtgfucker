package io.asv.mtgocr.ocrreader.feature.carddetail

import io.asv.mtgocr.ocrreader.feature.carddetail.R as DetailR

import io.asv.mtgocr.ocrreader.core.designsystem.R as CommonR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow

/** Snapshot detail only. No catalog/price requests, variant selection or inventory writes. */
@Composable
fun CollectionCardDetail(card: CollectionRow, onDismiss: () -> Unit,
    artwork: @Composable (String?, String, Modifier) -> Unit) {
    key(card.id) {
        AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("collection-detail"),
            title = { Text(card.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("detail-content"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    artwork(card.imageUrl, card.name, Modifier.fillMaxWidth().height(200.dp))
                    Text(stringResource(DetailR.string.detail_read_only), style = MaterialTheme.typography.bodySmall)
                    DetailField(stringResource(DetailR.string.detail_printing), card.printingUuid.orEmpty())
                    DetailField(stringResource(DetailR.string.detail_set), listOf(card.setCode, card.setName,
                        card.collectorNumber).filter { it.isNotBlank() }.joinToString(" · "))
                    Text(stringResource(CommonR.string.shell_row_count, card.quantity))
                    DetailField(stringResource(DetailR.string.detail_variant),
                        listOf(card.language, card.finish.orEmpty(), card.condition).filter { it.isNotBlank() }.joinToString(" · "))
                    DetailField(stringResource(DetailR.string.detail_price), card.storedPrice)
                    DetailField(stringResource(CommonR.string.shell_groups), card.groups.joinToString(", "))
                    DetailField(stringResource(CommonR.string.shell_decks), card.decks.joinToString(", "))
                    DetailField(stringResource(DetailR.string.detail_sideboard), card.sideboardDecks.joinToString(", "))
                    Text(pluralStringResource(DetailR.plurals.detail_evidence, card.evidenceCount, card.evidenceCount))
                    DetailField(stringResource(DetailR.string.detail_description), card.description)
                }
            },
            confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("detail-close")) {
                Text(stringResource(CommonR.string.shell_close))
            } })
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    if (value.isNotBlank()) Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
