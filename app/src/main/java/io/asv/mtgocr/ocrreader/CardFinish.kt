package io.asv.mtgocr.ocrreader

import java.util.Locale

/** Shared finish semantics for visual badges across every card presentation. */
object CardFinish {
    @JvmStatic
    fun isFoil(value: String?): Boolean = when (value.orEmpty().trim().lowercase(Locale.ROOT)) {
        "foil", "etched", "etched-foil", "foil-etched" -> true
        else -> false
    }
}
