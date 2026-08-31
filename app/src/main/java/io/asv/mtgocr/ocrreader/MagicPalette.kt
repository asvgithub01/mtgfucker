package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import java.util.Locale

/** Persists and applies the user's preferred Magic colour to every activity. */
object MagicPalette {
    const val GREEN = "green"
    const val RED = "red"
    const val BLUE = "blue"
    const val BLACK = "black"
    const val WHITE = "white"
    const val METAL = "metal"

    private const val PREFERENCES = "appearance_preferences"
    private const val KEY_PALETTE = "magic_palette"

    @JvmStatic
    fun normalizeId(value: String?): String {
        val normalized = value.orEmpty().trim().lowercase(Locale.ROOT)
        return when (normalized) {
            RED, BLUE, BLACK, WHITE, METAL -> normalized
            else -> GREEN
        }
    }

    @JvmStatic
    fun selectedId(context: Context): String = normalizeId(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_PALETTE, GREEN)
    )

    /** Returns false when the requested palette was already active. */
    @JvmStatic
    fun select(context: Context, paletteId: String?): Boolean {
        val normalized = normalizeId(paletteId)
        if (selectedId(context) == normalized) return false
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(KEY_PALETTE, normalized).apply()
        return true
    }

    @JvmStatic
    fun applyTheme(activity: Activity) = activity.setTheme(themeFor(selectedId(activity)))

    @StyleRes
    private fun themeFor(paletteId: String): Int = when (paletteId) {
        RED -> R.style.Theme_Mtg_Red
        BLUE -> R.style.Theme_Mtg_Blue
        BLACK -> R.style.Theme_Mtg_Black
        WHITE -> R.style.Theme_Mtg_White
        METAL -> R.style.Theme_Mtg_Metal
        else -> R.style.Theme_Mtg_Green
    }

    @ColorInt @JvmStatic
    fun primaryColor(context: Context): Int = resolveColor(
        context, com.google.android.material.R.attr.colorPrimary, Color.rgb(36, 91, 67)
    )

    @ColorInt @JvmStatic
    fun primaryVariantColor(context: Context): Int = resolveColor(
        context, com.google.android.material.R.attr.colorPrimaryVariant, Color.rgb(23, 60, 44)
    )

    @ColorInt @JvmStatic
    fun secondaryColor(context: Context): Int = resolveColor(
        context, com.google.android.material.R.attr.colorSecondary, Color.rgb(224, 182, 75)
    )

    @ColorInt @JvmStatic
    fun backgroundColor(context: Context): Int = resolveColor(
        context, android.R.attr.colorBackground, Color.rgb(246, 241, 231)
    )

    @ColorInt
    internal fun resolveColor(context: Context, @AttrRes attribute: Int, @ColorInt fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) context.getColorCompat(value.resourceId) else value.data
        } else fallback
    }

    @ColorInt
    private fun Context.getColorCompat(resourceId: Int): Int =
        androidx.core.content.ContextCompat.getColor(this, resourceId)
}
