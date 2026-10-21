package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.DataUtils
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.domain.collection.*
import io.asv.mtgocr.ocrreader.domain.library.*
import io.asv.mtgocr.ocrreader.model.DeckCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/** No DeckCatalogStore.load: it migrates and writes the inventory as a side effect. */
class LegacyLibraryShellRepository(context: Context, private val io: CoroutineDispatcher) : LibraryShellRepository {
    private val context = context.applicationContext
    private val collectionReader = LegacyCollectionReader(AndroidLegacyCollectionSource(this.context), io)

    override fun changes(libraryId: String): kotlinx.coroutines.flow.Flow<Unit> {
        val library = LibraryCatalog.availableLibraries(context).firstOrNull { it.id == libraryId }
            ?: return kotlinx.coroutines.flow.emptyFlow()
        val deck = if (libraryId == LibraryCatalog.DEFAULT_ID) "myDeckCatalog.Json"
            else "biblio_${libraryId}_DeckCatalog.Json"
        return legacyLibraryChanges(context.filesDir,
            setOf(library.fileName, library.fileName + ".bak", deck, deck + ".bak"))
    }

    override suspend fun read(libraryId: String): ShellReadResult = withContext(io) {
        if (LibraryCatalog.active(context).id != libraryId) {
            return@withContext ShellReadResult.Failed(ShellReadFailure.LIBRARY_CHANGED)
        }
        val collection = when (val result = collectionReader.read(libraryId)) {
            is CollectionReadResult.Ready -> result.snapshot
            CollectionReadResult.Missing -> CollectionSnapshot(libraryId, emptyList())
            is CollectionReadResult.Failed -> return@withContext ShellReadResult.Failed(ShellReadFailure.COLLECTION_UNREADABLE)
        }
        // Pin both filenames to the requested library, even if active changes A -> B -> A.
        // Same legacy convention as LibraryCatalog.deckCatalogFile, which accepts only active.
        val catalogFile = if (libraryId == LibraryCatalog.DEFAULT_ID) "myDeckCatalog.Json"
            else "biblio_${libraryId}_DeckCatalog.Json"
        val primary = File(context.filesDir, catalogFile)
        val backup = File(context.filesDir, catalogFile + ".bak")
        val value: Any? = DataUtils.readSerializable<DeckCatalog>(context, catalogFile)
        if (value !is DeckCatalog && (primary.exists() || backup.exists())) {
            return@withContext ShellReadResult.Failed(ShellReadFailure.DECKS_UNREADABLE)
        }
        val definitions = (value as? DeckCatalog)?.decks.orEmpty()
        if (definitions.any { it == null }) {
            return@withContext ShellReadResult.Failed(ShellReadFailure.DECKS_UNREADABLE)
        }
        val decks = definitions.map { DeckSummaryDefinition(it.name, it.formatId, it.createdAt) }
        if (LibraryCatalog.active(context).id != libraryId) {
            return@withContext ShellReadResult.Failed(ShellReadFailure.LIBRARY_CHANGED)
        }
        ShellReadResult.Ready(LibraryShellSnapshot(collection, Collections.unmodifiableList(decks)))
    }
}
