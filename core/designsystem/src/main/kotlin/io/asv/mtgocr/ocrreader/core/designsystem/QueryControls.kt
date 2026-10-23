package io.asv.mtgocr.ocrreader.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Stateless query actions; labels, enabled state and callbacks belong to the feature. */
@Composable
fun QueryActions(backLabel: String, refreshLabel: String, refreshing: Boolean,
    onBack: () -> Unit, onRefresh: () -> Unit, refreshModifier: Modifier = Modifier) {
    Row {
        TextButton(onClick = onBack) { Text(backLabel) }
        TextButton(onClick = onRefresh, enabled = !refreshing, modifier = refreshModifier) { Text(refreshLabel) }
    }
}

/** Presentation only. Selection and dismissal state remain owned by the feature. */
@Composable
fun QueryDetailDialog(title: String, closeLabel: String, onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(closeLabel) } })
}
