package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.PremiumAccess
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.domain.services.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/** Local observation only. Never initialize Firebase, inspect workers, or plan cleanup. */
internal class LegacyServicesReader(private val context: Context) : ServicesReader {
    override suspend fun read(): ServicesSnapshot = withContext(Dispatchers.IO) {
        val premium = PremiumAccess.isEnabled(context)
        val libraries = LibraryCatalog.libraries(context).map { library ->
            LibraryFiles(library.id, library.name, premium || library.id == LibraryCatalog.DEFAULT_ID,
                bytes(library.fileName), bytes(library.fileName + ".bak"))
        }
        ServicesSnapshot(premium, PriceCurrency.preferred(context),
            // Deliberately read only the existing local preference, not CloudLibrarySync/Firebase.
            context.getSharedPreferences("cloud_library_sync", Context.MODE_PRIVATE)
                .getBoolean("auto_sync_enabled", true), Collections.unmodifiableList(libraries))
    }
    private fun bytes(name: String): Long? {
        val root = context.filesDir.canonicalFile
        val file = File(root, name).canonicalFile
        require(file.parentFile == root) { "Library file outside private root" }
        return if (file.isFile) file.length() else null
    }
}
