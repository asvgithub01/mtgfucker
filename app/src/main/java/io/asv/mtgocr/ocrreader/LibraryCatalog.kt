package io.asv.mtgocr.ocrreader

import android.content.Context
import io.asv.mtgocr.ocrreader.model.Biblio
import java.util.Locale
import java.util.UUID

data class LibraryInfo(val id: String, val name: String, val fileName: String) {
    override fun toString(): String = name
}

/** Registry of independent legacy Biblio files. The historical default filename never changes. */
object LibraryCatalog {
    const val DEFAULT_ID = "default"
    const val DEFAULT_FILE = "myBiblio.Json"
    private const val DEFAULT_NAME = "Biblio principal"
    private const val PREFERENCES = "library_catalog"
    private const val KEY_LIBRARIES = "libraries"
    private const val KEY_ACTIVE_ID = "active_library_id"
    private const val PINNED_BACKGROUND_PREFIX = "pinned_background_"

    @JvmStatic
    fun libraries(context: Context): List<LibraryInfo> {
        val saved = preferences(context).getStringSet(KEY_LIBRARIES, emptySet()).orEmpty()
        val extras = saved.mapNotNull(::decode)
            .filter { it.id != DEFAULT_ID && it.fileName != DEFAULT_FILE }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
        return listOf(defaultLibrary()) + extras
    }

    @JvmStatic
    fun availableLibraries(context: Context): List<LibraryInfo> =
        if (PremiumAccess.isEnabled(context)) libraries(context) else listOf(defaultLibrary())

    @JvmStatic
    fun active(context: Context): LibraryInfo {
        if (!PremiumAccess.isEnabled(context)) return defaultLibrary()
        val activeId = preferences(context).getString(KEY_ACTIVE_ID, DEFAULT_ID).orEmpty()
        return libraries(context).firstOrNull { it.id == activeId } ?: defaultLibrary()
    }

    @JvmStatic
    fun activeFile(context: Context): String = active(context).fileName

    @JvmStatic
    @Synchronized
    fun select(context: Context, libraryId: String): LibraryInfo {
        val selected = availableLibraries(context).firstOrNull { it.id == libraryId }
            ?: defaultLibrary()
        preferences(context).edit().putString(KEY_ACTIVE_ID, selected.id).apply()
        return selected
    }

    @JvmStatic
    @Synchronized
    fun create(context: Context, requestedName: String): LibraryInfo {
        check(PremiumAccess.isEnabled(context)) { "El modo premium no está activo" }
        val name = cleanName(requestedName)
        require(name.isNotBlank()) { "Escribe un nombre para la biblioteca" }
        require(libraries(context).none { it.name.equals(name, ignoreCase = true) }) {
            "Ya existe una biblioteca con ese nombre"
        }
        val id = UUID.randomUUID().toString()
        val library = LibraryInfo(id, name, "biblio_$id.Json")
        val records = preferences(context).getStringSet(KEY_LIBRARIES, emptySet()).orEmpty().toMutableSet()
        records += encode(library)
        preferences(context).edit().putStringSet(KEY_LIBRARIES, records).apply()
        DataUtils.saveSerializable(context, Biblio(library.fileName, library.name), library.fileName)
        return library
    }

    @JvmStatic
    fun deckCatalogFile(context: Context): String {
        val library = active(context)
        return if (library.id == DEFAULT_ID) "myDeckCatalog.Json" else "biblio_${library.id}_DeckCatalog.Json"
    }

    @JvmStatic
    fun pinnedBackgroundId(context: Context, libraryId: String = active(context).id): String =
        preferences(context).getString(PINNED_BACKGROUND_PREFIX + libraryId, "").orEmpty()

    @JvmStatic
    fun pinBackground(context: Context, collectionItemId: String) {
        if (!PremiumAccess.isEnabled(context)) return
        preferences(context).edit()
            .putString(PINNED_BACKGROUND_PREFIX + active(context).id, collectionItemId)
            .apply()
    }

    @JvmStatic
    fun clearPinnedBackground(context: Context) {
        preferences(context).edit()
            .remove(PINNED_BACKGROUND_PREFIX + active(context).id)
            .apply()
    }

    internal fun cleanName(value: String): String = value
        .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(60)

    private fun defaultLibrary() = LibraryInfo(DEFAULT_ID, DEFAULT_NAME, DEFAULT_FILE)

    private fun encode(library: LibraryInfo): String =
        listOf(library.id, library.name, library.fileName).joinToString("\t")

    private fun decode(value: String): LibraryInfo? {
        val fields = value.split('\t', limit = 3)
        if (fields.size != 3 || fields.any { it.isBlank() }) return null
        if (!fields[0].matches(Regex("[a-zA-Z0-9-]+"))) return null
        if (!fields[2].matches(Regex("biblio_[a-zA-Z0-9-]+\\.Json"))) return null
        return LibraryInfo(fields[0], cleanName(fields[1]), fields[2])
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
