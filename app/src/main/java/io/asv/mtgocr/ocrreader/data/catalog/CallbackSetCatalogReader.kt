package io.asv.mtgocr.ocrreader.data.catalog

import io.asv.mtgocr.ocrreader.domain.catalog.SetCatalogReader
import io.asv.mtgocr.ocrreader.domain.catalog.SetSummary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import java.util.Collections

internal fun interface SetCatalogSource {
    fun load(callback: (List<SetSummary>, Throwable?) -> Unit)
}

/** Terminal callback API only; do not apply this adapter to loadCard's multiple deliveries. */
internal class CallbackSetCatalogReader(private val source: SetCatalogSource) : SetCatalogReader {
    override suspend fun read(): List<SetSummary> = callbackFlow {
        source.load { values, error ->
            if (error != null) close(error)
            else {
                trySend(Collections.unmodifiableList(ArrayList(values)))
                close()
            }
        }
        // Legacy loadSetCatalog has no cancellation handle. Closed channel discards late callbacks.
        awaitClose { }
    }.first()
}
