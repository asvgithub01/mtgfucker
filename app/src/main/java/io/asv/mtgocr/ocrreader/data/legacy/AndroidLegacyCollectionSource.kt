package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.DataUtils
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.domain.collection.CollectionReadFailure
import io.asv.mtgocr.ocrreader.model.Biblio
import java.io.File

internal class AndroidLegacyCollectionSource(context: Context) : LegacyCollectionSource {
    private val appContext = context.applicationContext

    override fun load(libraryId: String): LegacyCollectionLoad {
        val library = LibraryCatalog.availableLibraries(appContext).firstOrNull { it.id == libraryId }
            ?: return LegacyCollectionLoad.Failed(CollectionReadFailure.UNKNOWN_LIBRARY)
        val primary = File(appContext.filesDir, library.fileName)
        val backup = File(appContext.filesDir, library.fileName + ".bak")
        if (!primary.exists() && !backup.exists()) return LegacyCollectionLoad.Missing
        // Reuse the established primary/backup reader. Never repair or write from this path.
        val value: Any? = DataUtils.readSerializable<Biblio>(appContext, library.fileName)
        if (value !is Biblio) return LegacyCollectionLoad.Failed(CollectionReadFailure.UNREADABLE)
        return LegacyCollectionLoad.Loaded(value)
    }
}
