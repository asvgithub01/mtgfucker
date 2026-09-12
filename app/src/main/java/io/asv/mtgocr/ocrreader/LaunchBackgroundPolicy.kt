package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo

/** Chooses a usable launch image without letting a stale pin hide every random fallback. */
object LaunchBackgroundPolicy {
    @JvmStatic
    fun pinned(
        cards: List<CardInfo>,
        pinnedCollectionItemId: String,
        premium: Boolean
    ): CardInfo? {
        if (!premium || pinnedCollectionItemId.isBlank()) return null
        return cards.firstOrNull {
            it.collectionItemId == pinnedCollectionItemId && !it.imgPath.isNullOrBlank()
        }
    }

    @JvmStatic
    fun choose(
        cards: List<CardInfo>,
        pinnedCollectionItemId: String,
        premium: Boolean,
        randomIndex: (Int) -> Int
    ): CardInfo? {
        val withImages = cards.filter { !it.imgPath.isNullOrBlank() }
        if (withImages.isEmpty()) return null
        pinned(withImages, pinnedCollectionItemId, premium)?.let { return it }
        // Prefer cards whose Scryfall image also has an artwork-only crop. Older/imported cards
        // can still fall back to their original image instead of leaving the launcher empty.
        val artworkCandidates = withImages.filter { LaunchArtworkUrl.isAvailable(it.imgPath) }
            .ifEmpty { withImages }
        return artworkCandidates[randomIndex(artworkCandidates.size).coerceIn(artworkCandidates.indices)]
    }
}
