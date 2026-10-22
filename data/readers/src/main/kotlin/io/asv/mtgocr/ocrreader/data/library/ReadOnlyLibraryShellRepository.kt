package io.asv.mtgocr.ocrreader.data.library

import io.asv.mtgocr.ocrreader.domain.collection.*
import io.asv.mtgocr.ocrreader.domain.library.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Collections

sealed interface DeckReadResult {
    data class Ready(val decks: List<DeckSummaryDefinition>) : DeckReadResult
    object Unreadable : DeckReadResult
}

/** Read port only. Sources must use the explicit ID, with no repair/migration side effects. */
interface LibraryShellReadSource {
    fun activeLibraryId(): String
    suspend fun collection(libraryId: String): CollectionReadResult
    fun decks(libraryId: String): DeckReadResult
    fun changes(libraryId: String): Flow<Unit>
}

/** Coordinates projections; never receives a serialized legacy model or a writer. */
class ReadOnlyLibraryShellRepository(
    private val source: LibraryShellReadSource,
    private val io: CoroutineDispatcher
) : LibraryShellRepository {
    override fun changes(libraryId: String): Flow<Unit> = source.changes(libraryId)

    override suspend fun read(libraryId: String): ShellReadResult = withContext(io) {
        if (source.activeLibraryId() != libraryId) {
            return@withContext ShellReadResult.Failed(ShellReadFailure.LIBRARY_CHANGED)
        }
        val collection = when (val result = source.collection(libraryId)) {
            is CollectionReadResult.Ready -> result.snapshot
            CollectionReadResult.Missing -> CollectionSnapshot(libraryId, emptyList())
            is CollectionReadResult.Failed -> return@withContext ShellReadResult.Failed(ShellReadFailure.COLLECTION_UNREADABLE)
        }
        val decks = when (val result = source.decks(libraryId)) {
            is DeckReadResult.Ready -> result.decks
            DeckReadResult.Unreadable -> return@withContext ShellReadResult.Failed(ShellReadFailure.DECKS_UNREADABLE)
        }
        // Retain the legacy before/after check; it is not a transaction or a write lock.
        if (source.activeLibraryId() != libraryId) {
            return@withContext ShellReadResult.Failed(ShellReadFailure.LIBRARY_CHANGED)
        }
        ShellReadResult.Ready(LibraryShellSnapshot(collection,
            Collections.unmodifiableList(ArrayList(decks))))
    }
}
