package io.asv.mtgocr.ocrreader

/** One bounded burst, counting fresh live frames only. Does not relax matching thresholds. */
internal class SymbolRetryPolicy {
    var attempts = 0
        private set
    fun reset() { attempts = 0 }
    fun beginAttempt(): Int { attempts = (attempts + 1).coerceAtMost(MAX_ATTEMPTS); return attempts }
    fun shouldRetry(reason: String?, confirmed: Boolean): Boolean =
        attempts in 1 until MAX_ATTEMPTS && !confirmed && reason in RETRYABLE

    companion object {
        const val MAX_ATTEMPTS = 5
        const val CAMERA_SETTLE_MS = 900L
        private val RETRYABLE = setOf("sin_recorte_de_simbolo", "simbolo_ambiguo",
            "distancia_excesiva", "margen_insuficiente", "identidad_no_confirmada",
            "ausencia_posible_no_confirmada")

        fun frameReady(progress: Float, frames: Int, elapsedSinceCamera: Long): Boolean =
            progress >= 1f && frames >= 7 && elapsedSinceCamera >= CAMERA_SETTLE_MS
    }
}
