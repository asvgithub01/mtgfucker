package io.asv.mtgocr.ocrreader

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min

/** Coordinates in the EXIF-upright original photo, independent of decoding/display size. */
data class PhotoCardBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) : Serializable {
    val area: Float get() = max(0f, right - left) * max(0f, bottom - top)
    fun overlaps(other: PhotoCardBounds): Boolean {
        val intersection = max(0f, min(right, other.right) - max(left, other.left)) *
            max(0f, min(bottom, other.bottom) - max(top, other.top))
        return min(area, other.area) > 0f && intersection / min(area, other.area) > .6f
    }
    companion object { private const val serialVersionUID = 1L }
}

internal object PhotoDetectionGeometry {
    fun tiles(): List<PhotoCardBounds> = buildList {
        for (rows in 1..3) for (columns in 1..3) for (y in 0 until rows) for (x in 0 until columns) {
            val px = .15f / columns
            val py = .15f / rows
            add(PhotoCardBounds(max(0f, x.toFloat() / columns - px), max(0f, y.toFloat() / rows - py),
                min(1f, (x + 1f) / columns + px), min(1f, (y + 1f) / rows + py)))
        }
    }

    fun confident(score: Float, runner: Float?): Boolean = runner != null &&
        score.isFinite() && runner.isFinite() && score >= .70f && score - runner >= .08f
}
