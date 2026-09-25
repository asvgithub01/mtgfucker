package io.asv.mtgocr.ocrreader

/** Counts only the current consecutive scan batch, never copies already in the library. */
internal class RepeatedScanCopies {
    data class Key(val printingUuid: String, val finish: String, val language: String)
    private var key: Key? = null
    private var itemId: String? = null
    val collectionItemId: String? get() = itemId
    var saved = 0
        private set

    fun matches(candidate: Key) = key == candidate && saved > 0
    fun added(candidate: Key, collectionItemId: String, count: Int) {
        require(count > 0)
        if (key != candidate || itemId != collectionItemId) saved = 0
        key = candidate
        itemId = collectionItemId
        saved += count
    }
    fun removed(collectionItemId: String) {
        if (itemId == collectionItemId) saved = (saved - 1).coerceAtLeast(0)
    }
    fun editionChanged(collectionItemId: String) {
        if (itemId == collectionItemId) { key = null; itemId = null; saved = 0 }
    }
    fun replaceBatch(candidate: Key, collectionItemId: String, total: Int) {
        require(total in 1..MAX_COPIES)
        key = candidate; itemId = collectionItemId; saved = total
    }
    fun additional(totalText: String): Int? = totalText.trim().toIntOrNull()
        ?.takeIf { it in saved..MAX_COPIES && it > 0 }?.minus(saved)

    companion object { const val MAX_COPIES = 99 }
}
