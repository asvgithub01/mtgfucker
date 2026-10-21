package io.asv.mtgocr.ocrreader.domain.collection

/** Read-only projection, NOT a replacement for the serialized collection format. */
data class CollectionRow(
    val id: String,
    val name: String,
    val printingUuid: String?,
    val finish: String?,
    val language: String,
    val condition: String,
    val quantity: Int,
    val imageUrl: String?,
    val groups: List<String>,
    val decks: List<String>,
    val sideboardDecks: List<String>,
    val addedAt: Long = 0,
    val setName: String = "",
    val setCode: String = ""
)

data class CollectionSnapshot(val libraryId: String, val rows: List<CollectionRow>)

enum class CollectionReadFailure {
    UNKNOWN_LIBRARY, UNREADABLE, MISSING_IDENTITY, DUPLICATE_IDENTITY
}

sealed interface CollectionReadResult {
    data class Ready(val snapshot: CollectionSnapshot) : CollectionReadResult
    /** No primary or backup file exists. Distinct from an unreadable collection. */
    object Missing : CollectionReadResult
    data class Failed(val reason: CollectionReadFailure) : CollectionReadResult
}

/** Explicit library identity: never silently reads whichever library became active. */
fun interface CollectionReader {
    suspend fun read(libraryId: String): CollectionReadResult
}
