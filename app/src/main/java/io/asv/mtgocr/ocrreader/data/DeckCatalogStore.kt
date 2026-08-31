package io.asv.mtgocr.ocrreader.data

import android.content.Context
import io.asv.mtgocr.ocrreader.DataUtils
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.DeckCatalog
import io.asv.mtgocr.ocrreader.model.DeckDefinition

object DeckCatalogStore {
    private const val FILE_NAME = "myDeckCatalog.Json"

    @JvmStatic
    fun load(context: Context, collection: Biblio?): DeckCatalog {
        val catalog = DataUtils.readSerializable<DeckCatalog>(context, FILE_NAME) ?: DeckCatalog()
        var changed = false
        val known = catalog.decks.associateBy { it.name.lowercase() }.toMutableMap()
        // Treat old "group" assignments as decks once during the one-way UI rename. The marker is
        // important: otherwise removing a migrated deck assignment would be undone on next load.
        if (!catalog.legacyGroupsMigrated) {
            collection?.cards.orEmpty().forEach { card ->
                card.groups.forEach { oldName -> changed = card.addDeck(oldName) || changed }
            }
            catalog.legacyGroupsMigrated = true
            changed = true
        }
        collection?.cards.orEmpty().forEach { card ->
            card.decks.forEach { name ->
                if (known[name.lowercase()] == null) {
                    val definition = DeckDefinition(name, "free", card.addedAt)
                    catalog.decks.add(definition)
                    known[name.lowercase()] = definition
                    changed = true
                }
            }
        }
        if (changed) {
            save(context, catalog)
            collection?.let { DataUtils.saveSerializable(context, it, it.nameFile) }
        }
        return catalog
    }

    @JvmStatic
    fun upsert(context: Context, collection: Biblio?, name: String, formatId: String): DeckDefinition {
        val normalized = name.trim()
        val catalog = load(context, collection)
        val existing = catalog.decks.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        val result = existing?.also { it.formatId = formatId }
            ?: DeckDefinition(normalized, formatId, System.currentTimeMillis()).also { catalog.decks.add(it) }
        save(context, catalog)
        return result
    }

    @JvmStatic
    fun save(context: Context, catalog: DeckCatalog) {
        DataUtils.saveSerializable(context, catalog, FILE_NAME)
    }
}
