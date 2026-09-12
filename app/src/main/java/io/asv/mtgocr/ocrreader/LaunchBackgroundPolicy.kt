package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo

/** Chooses a usable launch image without letting a stale pin hide every random fallback. */
object LaunchBackgroundPolicy {
    @JvmStatic
    fun choose(
        cards: List<CardInfo>,
        pinnedCollectionItemId: String,
        premium: Boolean,
        randomIndex: (Int) -> Int
    ): CardInfo? {
        val withImages = cards.filter { !it.imgPath.isNullOrBlank() }
        if (withImages.isEmpty()) return null
        if (premium && pinnedCollectionItemId.isNotBlank()) {
            withImages.firstOrNull { it.collectionItemId == pinnedCollectionItemId }?.let { return it }
        }
        return withImages[randomIndex(withImages.size).coerceIn(withImages.indices)]
    }
}
