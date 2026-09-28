package io.asv.mtgocr.ocrreader

/** Monotonic active scanning time: model preparation and background time do not count. */
internal class ScanSessionClock(initialMillis: Long = 0) {
    private var accumulated = initialMillis.coerceAtLeast(0)
    private var startedAt: Long? = null
    fun resume(now: Long) { if (startedAt == null) startedAt = now }
    fun pause(now: Long) { accumulated = elapsed(now); startedAt = null }
    fun elapsed(now: Long): Long = accumulated + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
    companion object {
        fun format(millis: Long): String {
            val seconds = millis.coerceAtLeast(0) / 1000
            return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
        }
    }
}
