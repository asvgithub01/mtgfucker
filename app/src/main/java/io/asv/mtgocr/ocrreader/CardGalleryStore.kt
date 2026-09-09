package io.asv.mtgocr.ocrreader

import java.util.UUID

/** Keeps large gallery payloads out of Android Binder transactions. */
internal object CardGalleryStore {
    data class Page(
        val key: String,
        val cardName: String,
        val collectionItemId: String,
        val imageUrl: String,
        val label: String,
        val setCode: String,
        val collectorNumber: String,
        val priceLabel: String,
        val finish: String
    )

    private val sessions = object : LinkedHashMap<String, List<Page>>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Page>>?): Boolean =
            size > 4
    }

    @Synchronized
    fun put(pages: List<Page>): String = UUID.randomUUID().toString().also { sessions[it] = pages }

    @Synchronized
    fun get(token: String): List<Page>? = sessions[token]

    @Synchronized
    fun remove(token: String) {
        sessions.remove(token)
    }
}
