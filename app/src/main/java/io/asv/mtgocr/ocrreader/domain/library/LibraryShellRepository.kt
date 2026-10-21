package io.asv.mtgocr.ocrreader.domain.library

import io.asv.mtgocr.ocrreader.domain.collection.CollectionSnapshot

data class DeckSummaryDefinition(val name: String, val format: String, val createdAt: Long)
data class LibraryShellSnapshot(
    val collection: CollectionSnapshot,
    val decks: List<DeckSummaryDefinition>
)
enum class ShellReadFailure { LIBRARY_CHANGED, COLLECTION_UNREADABLE, DECKS_UNREADABLE }
sealed interface ShellReadResult {
    data class Ready(val snapshot: LibraryShellSnapshot) : ShellReadResult
    data class Failed(val reason: ShellReadFailure) : ShellReadResult
}
fun interface LibraryShellRepository {
    suspend fun read(libraryId: String): ShellReadResult
}
