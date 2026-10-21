package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.collection.CollectionReadFailure
import io.asv.mtgocr.ocrreader.domain.collection.CollectionReadResult
import io.asv.mtgocr.ocrreader.domain.collection.CollectionReader
import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow
import io.asv.mtgocr.ocrreader.domain.collection.CollectionSnapshot
import io.asv.mtgocr.ocrreader.model.Biblio
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Collections

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
    private val source: LegacyCollectionSource,
    private val ioDispatcher: CoroutineDispatcher
) : CollectionReader {
    override suspend fun read(libraryId: String): CollectionReadResult = withContext(ioDispatcher) {
        when (val loaded = source.load(libraryId)) {
            is LegacyCollectionLoad.Loaded -> project(libraryId, loaded.collection)
            LegacyCollectionLoad.Missing -> CollectionReadResult.Missing
            is LegacyCollectionLoad.Failed -> CollectionReadResult.Failed(loaded.reason)
        }
    }

    private fun project(libraryId: String, collection: Biblio): CollectionReadResult {
        // Getters on legacy models may initialize fields. Only touch detached copies.
        val detached = collection.snapshotForPersistence()
        val ids = HashSet<String>()
        val rows = ArrayList<CollectionRow>()
        for (card in detached.cards.orEmpty()) {
            if (card == null) {
                return CollectionReadResult.Failed(CollectionReadFailure.UNREADABLE)
            }
            // Do not expose random identities that change on each read of an old file.
            if (card.ensureCollectionItemId()) {
                return CollectionReadResult.Failed(CollectionReadFailure.MISSING_IDENTITY)
            }
            val id = card.collectionItemId
            if (!ids.add(id)) {
                return CollectionReadResult.Failed(CollectionReadFailure.DUPLICATE_IDENTITY)
            }
            rows += CollectionRow(
                id, card.name.orEmpty(), card.printingUuid, card.finish,
                card.languageCode, card.condition, card.quantityCount, card.imgPath,
                immutableCopy(card.groups), immutableCopy(card.decks), immutableCopy(card.sideboardDecks),
                card.addedAt, card.setName.orEmpty(), card.setCode.orEmpty()
            )
        }
        return CollectionReadResult.Ready(CollectionSnapshot(libraryId, immutableCopy(rows)))
    }

    private fun <T> immutableCopy(values: Collection<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))
}
