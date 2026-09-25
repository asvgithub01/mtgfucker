package io.asv.mtgocr.ocrreader

import android.content.Context

/** Scanner hardware preferences shared by the launcher and OCR screen. */
object ScannerSettings {
    private const val PREFERENCES = "scanner_preferences"
    private const val KEY_AUTO_FOCUS = "camera_auto_focus"
    private const val KEY_FLASH = "camera_flash"

    @JvmStatic
    fun autoFocus(context: Context): Boolean = preferences(context).getBoolean(KEY_AUTO_FOCUS, true)

    @JvmStatic
    fun flash(context: Context): Boolean = preferences(context).getBoolean(KEY_FLASH, false)

    /** Same preference as OcrCaptureActivity's existing Foil checkbox; not visual evidence. */
    @JvmStatic
    fun preferredFinish(context: Context): String =
        if (preferences(context).getBoolean("scan_foil", false)) "foil" else "nonfoil"

    @JvmStatic
    fun setAutoFocus(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_AUTO_FOCUS, enabled).apply()
    }

    @JvmStatic
    fun setFlash(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_FLASH, enabled).apply()
    }

    @JvmStatic
    fun enhancedOcr(context: Context): Boolean = preferences(context).getBoolean("enhanced_ocr_experiment", false)

    @JvmStatic
    fun setEnhancedOcr(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("enhanced_ocr_experiment", enabled).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
