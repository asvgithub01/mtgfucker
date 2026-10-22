package io.asv.mtgocr.ocrreader.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Reusable asynchronous status; the feature owns messages, state and retry behavior. */
@Composable
fun LoadStatus(
    loading: Boolean,
    errorMessage: String?,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, enabled = !loading) { Text(retryLabel) }
        }
    }
}
