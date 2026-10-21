package io.asv.mtgocr.ocrreader.legacybridge

import android.content.Context
import android.content.Intent
import io.asv.mtgocr.ocrreader.App
import io.asv.mtgocr.ocrreader.OcrCaptureActivity
import io.asv.mtgocr.ocrreader.ScannerSettings

/** Same Activity and extras as the former Java launcher. No scanner result contract invented. */
object LegacyLibraryNavigation {
    fun intent(context: Context, askStartupPriceRefresh: Boolean): Intent =
        Intent(context, OcrCaptureActivity::class.java)
            .putExtra(App.INTENT_AUTO_FOCUS, ScannerSettings.autoFocus(context))
            .putExtra(App.INTENT_USE_FLASH, ScannerSettings.flash(context))
            .putExtra(App.INTENT_PERSISTOR_MODE, "0")
            .putExtra("askStartupPriceRefresh", askStartupPriceRefresh)
}
