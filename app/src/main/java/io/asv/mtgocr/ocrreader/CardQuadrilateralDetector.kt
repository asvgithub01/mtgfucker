package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class DetectedCardQuad(
    val corners: Array<PointF>,
    val confidence: Double
)

/** Fits the four visible card edges around the scanner guide and intersects their lines. */
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
        val seed = CardFrameAnalyzer.analyze(bitmap).bounds
        val left = fitVerticalSide(bitmap, seed, seed.left)
        val right = fitVerticalSide(bitmap, seed, seed.right)
        val top = fitHorizontalSide(bitmap, seed, seed.top)
        val bottom = fitHorizontalSide(bitmap, seed, seed.bottom)
        val corners = arrayOf(
            intersection(left, top) ?: return null,
            intersection(right, top) ?: return null,
            intersection(right, bottom) ?: return null,
            intersection(left, bottom) ?: return null
        )
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        corners.forEach {
            it.x = it.x.coerceIn(0f, width)
            it.y = it.y.coerceIn(0f, height)
        }
        if (!CardCropAdjustView.validQuad(corners, width, height)) return null
        val confidence = listOf(left, right, top, bottom).map { it.confidence }.average()
        if (confidence < MIN_CONFIDENCE) return null
        return DetectedCardQuad(corners, confidence)
    }

    /** Fits x = slope * y + intercept. */
    private fun fitVerticalSide(bitmap: Bitmap, seed: Rect, expectedX: Int): FittedLine {
        val radius = max(8, (seed.width() * .16f).toInt())
        val offset = max(2, min(bitmap.width, bitmap.height) / 300)
        val samples = ArrayList<EdgeSample>(VERTICAL_SAMPLES)
        for (index in 0 until VERTICAL_SAMPLES) {
            val fraction = .08f + .84f * index / (VERTICAL_SAMPLES - 1f)
            val y = (seed.top + seed.height() * fraction).toInt()
                .coerceIn(offset, bitmap.height - offset - 1)
            samples += strongestVerticalAt(bitmap, expectedX, y, radius, offset)
        }
        return robustFit(samples, radius.toFloat())
    }

    /** Fits y = slope * x + intercept. */
    private fun fitHorizontalSide(bitmap: Bitmap, seed: Rect, expectedY: Int): FittedLine {
        val radius = max(8, (seed.height() * .12f).toInt())
        val offset = max(2, min(bitmap.width, bitmap.height) / 300)
        val samples = ArrayList<EdgeSample>(HORIZONTAL_SAMPLES)
        for (index in 0 until HORIZONTAL_SAMPLES) {
            val fraction = .08f + .84f * index / (HORIZONTAL_SAMPLES - 1f)
            val x = (seed.left + seed.width() * fraction).toInt()
                .coerceIn(offset, bitmap.width - offset - 1)
            val edge = strongestHorizontalAt(bitmap, expectedY, x, radius, offset)
            samples += EdgeSample(x.toFloat(), edge.position, edge.strength)
        }
        return robustFit(samples, radius.toFloat())
    }

    private fun strongestVerticalAt(
        bitmap: Bitmap,
        expectedX: Int,
        y: Int,
        radius: Int,
        offset: Int
    ): EdgeSample {
        val start = (expectedX - radius).coerceIn(offset, bitmap.width - offset - 1)
        val end = (expectedX + radius).coerceIn(start, bitmap.width - offset - 1)
        var bestPosition = expectedX.coerceIn(start, end)
        var bestStrength = 0.0
        for (x in start..end) {
            var raw = 0.0
            var count = 0
            for (sampleY in (y - 2).coerceAtLeast(0)..(y + 2).coerceAtMost(bitmap.height - 1)) {
                raw += colorDistance(
                    bitmap.getPixel(x - offset, sampleY),
                    bitmap.getPixel(x + offset, sampleY)
                )
                count++
            }
            val proximity = 1.0 - .28 * abs(x - expectedX) / radius.coerceAtLeast(1).toDouble()
            val strength = raw / count.coerceAtLeast(1) * proximity
            if (strength > bestStrength) {
                bestStrength = strength
                bestPosition = x
            }
        }
        return EdgeSample(y.toFloat(), bestPosition.toFloat(), bestStrength)
    }

    private fun strongestHorizontalAt(
        bitmap: Bitmap,
        expectedY: Int,
        x: Int,
        radius: Int,
        offset: Int
    ): EdgeAtPosition {
        val start = (expectedY - radius).coerceIn(offset, bitmap.height - offset - 1)
        val end = (expectedY + radius).coerceIn(start, bitmap.height - offset - 1)
        var bestPosition = expectedY.coerceIn(start, end).toFloat()
        var bestStrength = 0.0
        for (y in start..end) {
            var raw = 0.0
            var count = 0
            for (sampleX in (x - 2).coerceAtLeast(0)..(x + 2).coerceAtMost(bitmap.width - 1)) {
                raw += colorDistance(
                    bitmap.getPixel(sampleX, y - offset),
                    bitmap.getPixel(sampleX, y + offset)
                )
                count++
            }
            val proximity = 1.0 - .28 * abs(y - expectedY) / radius.coerceAtLeast(1).toDouble()
            val strength = raw / count.coerceAtLeast(1) * proximity
            if (strength > bestStrength) {
                bestStrength = strength
                bestPosition = y.toFloat()
            }
        }
        return EdgeAtPosition(bestPosition, bestStrength)
    }

    private fun robustFit(input: List<EdgeSample>, searchRadius: Float): FittedLine {
        var fitted = weightedFit(input)
        val maximumResidual = max(3f, searchRadius * .24f)
        val consistent = input.filter {
            abs(it.dependent - (fitted.slope * it.independent + fitted.intercept)) <= maximumResidual
        }
        if (consistent.size >= input.size / 2) fitted = weightedFit(consistent)
        val used = if (consistent.size >= input.size / 2) consistent else input
        val residual = used.map {
            abs(it.dependent - (fitted.slope * it.independent + fitted.intercept))
        }.average()
        val contrast = (used.map { it.strength }.average() / 105.0).coerceIn(0.0, 1.0)
        val consistency = (1.0 - residual / searchRadius.coerceAtLeast(1f)).coerceIn(0.0, 1.0)
        return fitted.copy(confidence = contrast * .68 + consistency * .32)
    }

    private fun weightedFit(samples: List<EdgeSample>): FittedLine {
        val weights = samples.map { it.strength.coerceAtLeast(1.0) }
        val totalWeight = weights.sum().coerceAtLeast(1.0)
        val meanIndependent = samples.indices.sumOf {
            samples[it].independent.toDouble() * weights[it]
        } / totalWeight
        val meanDependent = samples.indices.sumOf {
            samples[it].dependent.toDouble() * weights[it]
        } / totalWeight
        var numerator = 0.0
        var denominator = 0.0
        for (index in samples.indices) {
            val dx = samples[index].independent - meanIndependent
            numerator += weights[index] * dx * (samples[index].dependent - meanDependent)
            denominator += weights[index] * dx * dx
        }
        val slope = if (denominator <= .0001) 0f else (numerator / denominator).toFloat()
        return FittedLine(slope, (meanDependent - slope * meanIndependent).toFloat(), 0.0)
    }

    private fun intersection(vertical: FittedLine, horizontal: FittedLine): PointF? {
        val denominator = 1f - vertical.slope * horizontal.slope
        if (abs(denominator) < .02f) return null
        val x = (vertical.slope * horizontal.intercept + vertical.intercept) / denominator
        return PointF(x, horizontal.slope * x + horizontal.intercept)
    }

    private fun colorDistance(left: Int, right: Int): Double {
        val red = Color.red(left) - Color.red(right)
        val green = Color.green(left) - Color.green(right)
        val blue = Color.blue(left) - Color.blue(right)
        return sqrt((red * red + green * green + blue * blue).toDouble())
    }

    private data class EdgeSample(
        val independent: Float,
        val dependent: Float,
        val strength: Double
    )

    private data class EdgeAtPosition(val position: Float, val strength: Double)

    private data class FittedLine(
        val slope: Float,
        val intercept: Float,
        val confidence: Double
    )

    private const val MAX_ANALYSIS_SIDE = 720f
    private const val VERTICAL_SAMPLES = 27
    private const val HORIZONTAL_SAMPLES = 21
    private const val MIN_CONFIDENCE = .30
}
