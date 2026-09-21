package io.asv.mtgocr.ocrreader

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Rejects quadrilaterals that cannot be a near-frontal 63×88 MTG card. */
internal object CardAspectPolicy {
    const val EXPECTED = 63.0 / 88.0
    private const val MAX_RELATIVE_ERROR = .12
    private const val MIN_OPPOSITE_EDGE_BALANCE = .70

    data class Measurement(val ratio: Double, val fit: Double)

    fun measure(top: Double, right: Double, bottom: Double, left: Double): Measurement? {
        if (minOf(top, right, bottom, left) <= 1.0) return null
        val cardWidth = (top + bottom) / 2.0
        val cardHeight = (left + right) / 2.0
        if (cardHeight <= cardWidth) return null
        val ratio = cardWidth / cardHeight
        val relativeError = abs(ratio - EXPECTED) / EXPECTED
        if (relativeError > MAX_RELATIVE_ERROR) return null
        val widthBalance = min(top, bottom) / max(top, bottom)
        val heightBalance = min(left, right) / max(left, right)
        if (widthBalance < MIN_OPPOSITE_EDGE_BALANCE ||
            heightBalance < MIN_OPPOSITE_EDGE_BALANCE
        ) return null
        return Measurement(ratio, (1.0 - relativeError / MAX_RELATIVE_ERROR).coerceIn(0.0, 1.0))
    }
}
