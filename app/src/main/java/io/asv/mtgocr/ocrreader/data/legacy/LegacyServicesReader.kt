package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.PremiumAccess
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.domain.services.*
import io.asv.mtgocr.ocrreader.data.services.*
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Local observation only. Never initialize Firebase, inspect workers, or plan cleanup. */
internal class LegacyServicesReader(context: Context) : ServicesReader by
    ReadOnlyServicesReader(AndroidServicesReadSource(context), Dispatchers.IO)

private class AndroidServicesReadSource(private val context: Context) : ServicesReadSource {
    override fun premiumEnabled(): Boolean = PremiumAccess.isEnabled(context)

    override fun libraries(): List<ServiceLibraryReference> = LibraryCatalog.libraries(context).map { library ->
        ServiceLibraryReference(library.id, library.name, library.id == LibraryCatalog.DEFAULT_ID,
            library.fileName, library.fileName + ".bak")
    }

    override fun preferredCurrency(): String = PriceCurrency.preferred(context)

    // Deliberately read only the existing local preference, not CloudLibrarySync/Firebase.
    override fun autoSyncPreference(): Boolean =
        context.getSharedPreferences("cloud_library_sync", Context.MODE_PRIVATE)
            .getBoolean("auto_sync_enabled", true)

    override fun bytes(fileName: String): Long? {
        val root = context.filesDir.canonicalFile
        val file = File(root, fileName).canonicalFile
        require(file.parentFile == root) { "Library file outside private root" }
        return if (file.isFile) file.length() else null
    }
}
