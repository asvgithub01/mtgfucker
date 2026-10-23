package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.PremiumAccess
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.domain.services.*
import io.asv.mtgocr.ocrreader.data.services.*

internal class AndroidServicesReadSource(private val context: Context) : ServicesReadSource {
    private val files = PrivateLibraryFileReader { context.filesDir }
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

    override fun bytes(fileName: String): Long? = files.bytes(fileName)
}
