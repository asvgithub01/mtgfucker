package io.asv.collectorvision

/** Inclusive doubling thresholds, always cumulative. Independent of Android for boundary tests. */
internal data class ScanPartyOptions private constructor(val level: Int) {
    val balloons: Boolean get() = level >= 2
    val trumpets: Boolean get() = level >= 3
    val disco: Boolean get() = level >= 4
    val dancer: Boolean get() = level >= 5
    val durationMillis: Long get() = if (dancer) 5000L else 4000L

    companion object {
        fun forPrice(euros: Double, threshold: Double): ScanPartyOptions? {
            if (!PriceCelebration.eligible(euros, threshold)) return null
            val level = when {
                euros >= threshold * 16 -> 5
                euros >= threshold * 8 -> 4
                euros >= threshold * 4 -> 3
                euros >= threshold * 2 -> 2
                else -> 1
            }
            return ScanPartyOptions(level)
        }
    }
}
