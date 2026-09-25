package io.asv.mtgocr.ocrreader

/** Session-only continuity: remember a successfully saved printing, never a tentative ranking. */
internal class ConsecutiveArtworkEdition {
    private var artwork: String? = null
    private var printing: String? = null
    private var itemId: String? = null

    fun observe(artworkId: String?) {
        if (artworkId.isNullOrBlank() || artworkId != artwork) clear()
    }

    fun remember(artworkId: String?, printingUuid: String, collectionItemId: String) {
        if (artworkId.isNullOrBlank()) { clear(); return }
        artwork = artworkId; printing = printingUuid; itemId = collectionItemId
    }

    fun retainedPrinting(artworkId: String?, availableUuids: Collection<String>): String? =
        printing?.takeIf { !artworkId.isNullOrBlank() && artworkId == artwork && it in availableUuids }

    fun corrected(collectionItemId: String, printingUuid: String) {
        if (itemId == collectionItemId) printing = printingUuid
    }

    fun removed(collectionItemId: String, remaining: Int) {
        if (itemId == collectionItemId && remaining <= 0) clear()
    }

    private fun clear() { artwork = null; printing = null; itemId = null }
}
