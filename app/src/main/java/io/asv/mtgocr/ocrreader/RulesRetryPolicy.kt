package io.asv.mtgocr.ocrreader

/** Fresh captures only. Never combines evidence, remembers a printing, or weakens acceptance. */
internal class RulesRetryPolicy {
    var attempts = 0
        private set
    fun beginAttempt(): Int { attempts++; return attempts }
    fun reset() { attempts = 0 }
    fun shouldRetry(reason: String, live: Boolean, enabled: Boolean): Boolean =
        live && enabled && attempts in 1 until MAX_ATTEMPTS && reason in RETRYABLE

    companion object {
        const val MAX_ATTEMPTS = 3
        private val RETRYABLE = setOf("NO_ART", "WEAK_ART", "ART_MARGIN", "OCR_CONFLICT",
            "CONFLICT", "INCOMPLETE_CAPTURE", "MULTIPLE_SETS", "MULTIPLE_PRINTINGS",
            "FOOTER_NEEDS_CORROBORATION", "FOOTER_CONFLICT", "FOOTER_NOT_IN_ART_CATALOGUE",
            "LANGUAGE_UNKNOWN", "LANGUAGE_UNAVAILABLE")
    }
}

internal object ScannerZoomPolicy {
    fun clamp(ratio: Float, min: Float, max: Float): Float =
        (if (ratio.isFinite() && ratio > 0f) ratio else 1f).coerceIn(min, max)

    fun fromLinear(linear: Float, min: Float, max: Float): Float {
        val value = if (linear.isFinite()) linear.coerceIn(0f, 1f) else 0f
        return clamp(1f / ((1f - value) / min + value / max), min, max)
    }
}
