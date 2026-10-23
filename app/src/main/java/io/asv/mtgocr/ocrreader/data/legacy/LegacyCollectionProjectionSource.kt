package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.collection.CollectionReadFailure
import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow
import io.asv.mtgocr.ocrreader.model.Biblio
import java.util.Collections

/** Legacy getters may initialize fields: map only a detached graph, never the live collection. */
internal class LegacyCollectionProjectionSource(private val source: LegacyCollectionSource) : CollectionReadSource {
    override fun load(libraryId: String): CollectionRowsLoad = when (val loaded = source.load(libraryId)) {
        LegacyCollectionLoad.Missing -> CollectionRowsLoad.Missing
        is LegacyCollectionLoad.Failed -> CollectionRowsLoad.Failed(loaded.reason)
        is LegacyCollectionLoad.Loaded -> detachedRows(loaded.collection)
    }

    private fun detachedRows(collection: Biblio): CollectionRowsLoad.Loaded {
        val detached = collection.snapshotForPersistence()
        return CollectionRowsLoad.Loaded(detached.cards.orEmpty().asSequence().map { card ->
            if (card == null) {
                return@map CollectionRowRead.Failed(CollectionReadFailure.UNREADABLE)
            }
            // Never publish newly generated identities or write an upgrade from a read.
            if (card.ensureCollectionItemId()) {
                return@map CollectionRowRead.Failed(CollectionReadFailure.MISSING_IDENTITY)
            }
            val id = card.collectionItemId
            CollectionRowRead.Ready(id) {
                CollectionRow(
                    id, card.name.orEmpty(), card.printingUuid, card.finish,
                    card.languageCode, card.condition, card.quantityCount, card.imgPath,
                    immutableCopy(card.groups), immutableCopy(card.decks), immutableCopy(card.sideboardDecks),
                    card.addedAt, card.setName.orEmpty(), card.setCode.orEmpty(),
                    card.collectorNumber.orEmpty(), card.description.orEmpty(), card.price.orEmpty(),
                    card.scanMetadataHistory.size
                )
            }
        })
    }

    private fun <T> immutableCopy(values: Collection<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))
}
