package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class DetectedCardQuad(
    val corners: Array<PointF>,
    val confidence: Double
)

/** Finds four coherent card-edge lines near the scanner guide and intersects their corners. */
object CardQuadrilateralDetector {
    fun detect(bitmap: Bitmap): DetectedCardQuad? {
        val longestSide = max(bitmap.width, bitmap.height)
        val scale = min(1f, MAX_ANALYSIS_SIDE / longestSide.toFloat())
        val working = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).toInt()),
            max(1, (bitmap.height * scale).toInt()),
            true
        ) else bitmap
        return try {
            detectWorking(working)?.let { detected ->
                if (scale == 1f) detected else DetectedCardQuad(
                    detected.corners.map { PointF(it.x / scale, it.y / scale) }.toTypedArray(),
                    detected.confidence
                )
            }
        } finally {
            if (working !== bitmap) working.recycle()
        }
    }

    private fun detectWorking(bitmap: Bitmap): DetectedCardQuad? {
        // Searching complete coherent lines prevents isolated text or artwork edges from pulling
        // different samples to unrelated positions, which happened in the first detector.
        val expected = CardImageFingerprint.centeredCardRect(bitmap.width, bitmap.height, .72f)
        val centerX = expected.exactCenterX()
        val centerY = expected.exactCenterY()
        val verticalRadius = max(10, (expected.width() * .22f).toInt())
        val horizontalRadius = max(10, (expected.height() * .16f).toInt())
        val offset = max(2, min(bitmap.width, bitmap.height) / 280)

        val left = searchVerticalLine(
            bitmap, expected.left, centerY, expected.top, expected.bottom, verticalRadius, offset
        )
        val right = searchVerticalLine(
            bitmap, expected.right, centerY, expected.top, expected.bottom, verticalRadius, offset
        )
        val top = searchHorizontalLine(
            bitmap, expected.top, centerX, expected.left, expected.right, horizontalRadius, offset
        )
        val bottom = searchHorizontalLine(
            bitmap, expected.bottom, centerX, expected.left, expected.right, horizontalRadius, offset
        )

        val corners = arrayOf(
            intersection(left, top) ?: return null,
            intersection(right, top) ?: return null,
            intersection(right, bottom) ?: return null,
            intersection(left, bottom) ?: return null
        )
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        if (corners.any { it.x !in 0f..width || it.y !in 0f..height }) return null
        if (!CardCropAdjustView.validQuad(corners, width, height)) return null
        if (!plausibleCardShape(corners)) return null

        val confidence = listOf(left, right, top, bottom).map { it.confidence }.average()
        if (confidence < MIN_CONFIDENCE) return null
        return DetectedCardQuad(corners, confidence)
    }

    /** x = position + slope * (y - axisCenter). */
    private fun searchVerticalLine(
        bitmap: Bitmap,
        expectedPosition: Int,
        axisCenter: Float,
        rangeStart: Int,
        rangeEnd: Int,
        radius: Int,
        offset: Int
    ): EdgeLine {
        var best = EdgeLine(expectedPosition.toFloat(), 0f, axisCenter, 0.0)
        val positionStep = max(1, bitmap.width / 520)
        var position = expectedPosition - radius
        while (position <= expectedPosition + radius) {
            var slope = -MAX_SLOPE
            while (slope <= MAX_SLOPE + .001f) {
                val strength = verticalLineStrength(
                    bitmap, position.toFloat(), slope, axisCenter, rangeStart, rangeEnd, offset
                )
                val proximity = 1.0 - POSITION_PENALTY *
                    abs(position - expectedPosition) / radius.coerceAtLeast(1).toDouble()
                val score = strength * proximity
                if (score > best.confidence) {
                    best = EdgeLine(position.toFloat(), slope, axisCenter, score)
                }
                slope += SLOPE_STEP
            }
            position += positionStep
        }
        return best.copy(confidence = (best.confidence / EDGE_NORMALIZER).coerceIn(0.0, 1.0))
    }

    /** y = position + slope * (x - axisCenter). */
    private fun searchHorizontalLine(
        bitmap: Bitmap,
        expectedPosition: Int,
        axisCenter: Float,
        rangeStart: Int,
        rangeEnd: Int,
        radius: Int,
        offset: Int
    ): EdgeLine {
        var best = EdgeLine(expectedPosition.toFloat(), 0f, axisCenter, 0.0)
        val positionStep = max(1, bitmap.height / 700)
        var position = expectedPosition - radius
        while (position <= expectedPosition + radius) {
            var slope = -MAX_SLOPE
            while (slope <= MAX_SLOPE + .001f) {
                val strength = horizontalLineStrength(
                    bitmap, position.toFloat(), slope, axisCenter, rangeStart, rangeEnd, offset
                )
                val proximity = 1.0 - POSITION_PENALTY *
                    abs(position - expectedPosition) / radius.coerceAtLeast(1).toDouble()
                val score = strength * proximity
                if (score > best.confidence) {
                    best = EdgeLine(position.toFloat(), slope, axisCenter, score)
                }
                slope += SLOPE_STEP
            }
            position += positionStep
        }
        return best.copy(confidence = (best.confidence / EDGE_NORMALIZER).coerceIn(0.0, 1.0))
    }

    private fun verticalLineStrength(
        bitmap: Bitmap,
        position: Float,
        slope: Float,
        centerY: Float,
        top: Int,
        bottom: Int,
        offset: Int
    ): Double {
        val start = top + ((bottom - top) * .07f).toInt()
        val end = bottom - ((bottom - top) * .07f).toInt()
        val step = max(2, (end - start) / LINE_SAMPLES)
        var total = 0.0
        var count = 0
        var y = start
        while (y <= end) {
            val x = (position + slope * (y - centerY)).toInt()
            if (x - offset - 1 >= 0 && x + offset + 1 < bitmap.width && y in 0 until bitmap.height) {
                var strongest = 0.0
                for (shift in -1..1) strongest = max(
                    strongest,
                    colorDistance(
                        bitmap.getPixel(x - offset + shift, y),
                        bitmap.getPixel(x + offset + shift, y)
                    )
                )
                total += strongest
                count++
            }
            y += step
        }
        return if (count == 0) 0.0 else total / count
    }

    private fun horizontalLineStrength(
        bitmap: Bitmap,
        position: Float,
        slope: Float,
        centerX: Float,
        left: Int,
        right: Int,
        offset: Int
    ): Double {
        val start = left + ((right - left) * .07f).toInt()
        val end = right - ((right - left) * .07f).toInt()
        val step = max(2, (end - start) / LINE_SAMPLES)
        var total = 0.0
        var count = 0
        var x = start
        while (x <= end) {
            val y = (position + slope * (x - centerX)).toInt()
            if (y - offset - 1 >= 0 && y + offset + 1 < bitmap.height && x in 0 until bitmap.width) {
                var strongest = 0.0
                for (shift in -1..1) strongest = max(
                    strongest,
                    colorDistance(
                        bitmap.getPixel(x, y - offset + shift),
                        bitmap.getPixel(x, y + offset + shift)
                    )
                )
                total += strongest
                count++
            }
            x += step
        }
        return if (count == 0) 0.0 else total / count
    }

    private fun intersection(vertical: EdgeLine, horizontal: EdgeLine): PointF? {
        val verticalIntercept = vertical.position - vertical.slope * vertical.axisCenter
        val horizontalIntercept = horizontal.position - horizontal.slope * horizontal.axisCenter
        val denominator = 1f - vertical.slope * horizontal.slope
        if (abs(denominator) < .04f) return null
        val x = (vertical.slope * horizontalIntercept + verticalIntercept) / denominator
        return PointF(x, horizontal.slope * x + horizontalIntercept)
    }

    private fun plausibleCardShape(points: Array<PointF>): Boolean {
        val top = distance(points[0], points[1])
        val bottom = distance(points[3], points[2])
        val left = distance(points[0], points[3])
        val right = distance(points[1], points[2])
        val ratio = (top + bottom) / (left + right).coerceAtLeast(1f)
        return ratio in .50f..1.0f
    }

    private fun distance(first: PointF, second: PointF): Float = sqrt(
        (first.x - second.x) * (first.x - second.x) +
            (first.y - second.y) * (first.y - second.y)
    )

    private fun colorDistance(left: Int, right: Int): Double {
        val red = Color.red(left) - Color.red(right)
        val green = Color.green(left) - Color.green(right)
        val blue = Color.blue(left) - Color.blue(right)
        return sqrt((red * red + green * green + blue * blue).toDouble())
    }

    private data class EdgeLine(
        val position: Float,
        val slope: Float,
        val axisCenter: Float,
        val confidence: Double
    )

    private const val MAX_ANALYSIS_SIDE = 640f
    private const val MAX_SLOPE = .32f
    private const val SLOPE_STEP = .02f
    private const val POSITION_PENALTY = .34
    private const val LINE_SAMPLES = 64
    private const val EDGE_NORMALIZER = 92.0
    private const val MIN_CONFIDENCE = .33
}
