package io.asv.mtgocr.ocrreader

import android.content.Context

/** Temporary local gate that can later be replaced by the subscription entitlement. */
object PremiumAccess {
    private const val PREFERENCES = "premium_access"
    private const val KEY_ENABLED = "premium_enabled"

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (!enabled) LibraryCatalog.select(context, LibraryCatalog.DEFAULT_ID)
    }
}
