package io.asv.mtgocr.ocrreader.legacybridge

import android.content.Context
import android.content.Intent
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.TripleScanActivity

enum class ShellLegacyDestination { LIBRARY, TRIPLE }

/** Never switch active library to make a stale navigation request succeed. */
object LegacyShellNavigation {
    fun intent(context: Context, libraryId: String, destination: ShellLegacyDestination): Intent? {
        if (LibraryCatalog.active(context).id != libraryId) return null
        return when (destination) {
            ShellLegacyDestination.LIBRARY -> LegacyLibraryNavigation.intent(context, false)
            ShellLegacyDestination.TRIPLE -> Intent(context, TripleScanActivity::class.java)
        }
    }
}
