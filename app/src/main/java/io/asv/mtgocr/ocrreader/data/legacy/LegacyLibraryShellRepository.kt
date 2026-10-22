package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.DataUtils
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.domain.collection.*
import io.asv.mtgocr.ocrreader.domain.library.*
import io.asv.mtgocr.ocrreader.data.library.*
import io.asv.mtgocr.ocrreader.model.DeckCatalog
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

/** No DeckCatalogStore.load: it migrates and writes the inventory as a side effect. */
class LegacyLibraryShellRepository(context: Context, io: CoroutineDispatcher) : LibraryShellRepository by
    ReadOnlyLibraryShellRepository(AndroidLibraryShellReadSource(context, io), io)

private class AndroidLibraryShellReadSource(context: Context, io: CoroutineDispatcher) : LibraryShellReadSource {
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

    override fun activeLibraryId(): String = LibraryCatalog.active(context).id

    override suspend fun collection(libraryId: String): CollectionReadResult = collectionReader.read(libraryId)

    override fun decks(libraryId: String): DeckReadResult {
        // Pin both filenames to the requested library, even if active changes A -> B -> A.
        // Same legacy convention as LibraryCatalog.deckCatalogFile, which accepts only active.
        val catalogFile = if (libraryId == LibraryCatalog.DEFAULT_ID) "myDeckCatalog.Json"
            else "biblio_${libraryId}_DeckCatalog.Json"
        val primary = File(context.filesDir, catalogFile)
        val backup = File(context.filesDir, catalogFile + ".bak")
        val value: Any? = DataUtils.readSerializable<DeckCatalog>(context, catalogFile)
        if (value !is DeckCatalog && (primary.exists() || backup.exists())) {
            return DeckReadResult.Unreadable
        }
        val definitions = (value as? DeckCatalog)?.decks.orEmpty()
        if (definitions.any { it == null }) {
            return DeckReadResult.Unreadable
        }
        val decks = definitions.map { DeckSummaryDefinition(it.name, it.formatId, it.createdAt) }
        return DeckReadResult.Ready(decks)
    }
}
