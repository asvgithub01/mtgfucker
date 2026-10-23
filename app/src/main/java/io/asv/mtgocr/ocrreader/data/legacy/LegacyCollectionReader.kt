package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.collection.CollectionReadFailure
import io.asv.mtgocr.ocrreader.domain.collection.CollectionReader
import io.asv.mtgocr.ocrreader.model.Biblio
import kotlinx.coroutines.CoroutineDispatcher

/** Source returns a freshly deserialized graph, never the scanner's static in-memory graph. */
internal fun interface LegacyCollectionSource {
    fun load(libraryId: String): LegacyCollectionLoad
}

internal sealed interface LegacyCollectionLoad {
    data class Loaded(val collection: Biblio) : LegacyCollectionLoad
    object Missing : LegacyCollectionLoad
    data class Failed(val reason: CollectionReadFailure) : LegacyCollectionLoad
}

internal class LegacyCollectionReader(
    source: LegacyCollectionSource,
    ioDispatcher: CoroutineDispatcher
) : CollectionReader by ReadOnlyCollectionReader(LegacyCollectionProjectionSource(source), ioDispatcher)
