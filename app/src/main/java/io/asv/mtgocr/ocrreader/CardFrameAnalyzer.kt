package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class CardBorderSide { TOP, RIGHT, BOTTOM, LEFT }

data class CardBorderZone(
    val side: CardBorderSide,
    val position: Float,
    val x: Int,
    val y: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val color: CardBorderColor
)

data class CardFrameAnalysis(
    val bounds: Rect,
    val boundaryConfidence: Double,
    val borderColor: CardBorderColor,
    val borderConfidence: Double,
    val borderZones: List<CardBorderZone>,
    val glareRatio: Double,
    val sharpness: Double
) {
    val borderCounts: Map<CardBorderColor, Int>
        get() = borderZones.groupingBy { it.color }.eachCount()
}

/**
 * Finds the complete card around the camera guide and records explainable visual evidence.
 * The first version deliberately uses cheap edge profiles instead of a large CV dependency: the
 * dedicated screen already asks the user to keep the card upright, while the detector corrects the
 * remaining displacement before artwork, symbol and border regions are read.
 */
object CardFrameAnalyzer {
    private const val CARD_HEIGHT_FRACTION = .72f
    private val samplePositions = floatArrayOf(.16f, .38f, .62f, .84f)

    fun analyze(bitmap: Bitmap): CardFrameAnalysis {
        val expected = CardImageFingerprint.centeredCardRect(
            bitmap.width,
            bitmap.height,
            CARD_HEIGHT_FRACTION
        )
        val horizontalRadius = max(8, bitmap.width / 11)
        val verticalRadius = max(8, bitmap.height / 13)
        val edgeOffset = max(2, min(bitmap.width, bitmap.height) / 260)

        val left = strongestVerticalEdge(
            bitmap,
            expected.left,
            horizontalRadius,
            expected.top,
            expected.bottom,
            edgeOffset
        )
        val right = strongestVerticalEdge(
            bitmap,
            expected.right,
            horizontalRadius,
            expected.top,
            expected.bottom,
            edgeOffset
        )
        val provisionalLeft = min(left.coordinate, right.coordinate - 2)
        val provisionalRight = max(right.coordinate, provisionalLeft + 2)
        val top = strongestHorizontalEdge(
            bitmap,
            expected.top,
            verticalRadius,
            provisionalLeft,
            provisionalRight,
            edgeOffset
        )
        val bottom = strongestHorizontalEdge(
            bitmap,
            expected.bottom,
            verticalRadius,
            provisionalLeft,
            provisionalRight,
            edgeOffset
        )

        val detected = Rect(
            provisionalLeft.coerceIn(0, bitmap.width - 2),
            min(top.coordinate, bottom.coordinate - 2).coerceIn(0, bitmap.height - 2),
            provisionalRight.coerceIn(2, bitmap.width),
            max(bottom.coordinate, top.coordinate + 2).coerceIn(2, bitmap.height)
        )
        val aspect = detected.width().toDouble() / detected.height().coerceAtLeast(1)
        val aspectFit = (1.0 - abs(aspect - CARD_ASPECT) / CARD_ASPECT).coerceIn(0.0, 1.0)
        val edgeConfidence = listOf(left, right, top, bottom).map { it.confidence }.average()
        val boundaryConfidence = (edgeConfidence * .72 + aspectFit * .28).coerceIn(0.0, 1.0)

        // A very weak edge profile is safer when it falls back to the stable on-screen guide.
        val bounds = if (boundaryConfidence >= .24) detected else expected
        return analyzeBounds(bitmap, bounds, boundaryConfidence)
    }

    /** A manually corrected photo is already a perspective-normalised, tightly cropped card. */
    fun analyzeTightCard(bitmap: Bitmap): CardFrameAnalysis = analyzeBounds(
        bitmap,
        Rect(0, 0, bitmap.width, bitmap.height),
        1.0
    )

    private fun analyzeBounds(
        bitmap: Bitmap,
        bounds: Rect,
        boundaryConfidence: Double
    ): CardFrameAnalysis {
        val zones = sampleBorderZones(bitmap, bounds)
        val classified = classifyBorderZones(zones)
        return CardFrameAnalysis(
            bounds = bounds,
            boundaryConfidence = boundaryConfidence,
            borderColor = classified.first,
            borderConfidence = classified.second,
            borderZones = zones,
            glareRatio = glareRatio(bitmap, bounds),
            sharpness = sharpness(bitmap, bounds)
        )
    }

    fun annotatedPreview(bitmap: Bitmap, analysis: CardFrameAnalysis): Bitmap {
        val targetWidth = min(640, bitmap.width)
        val scale = targetWidth.toFloat() / bitmap.width.coerceAtLeast(1)
        val targetHeight = max(1, (bitmap.height * scale).toInt())
        val preview = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            .copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(preview)
        val densityScale = max(.75f, targetWidth / 480f)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 215, 92)
            style = Paint.Style.STROKE
            strokeWidth = 4f * densityScale
        }
        val bounds = RectF(
            analysis.bounds.left * scale,
            analysis.bounds.top * scale,
            analysis.bounds.right * scale,
            analysis.bounds.bottom * scale
        )
        canvas.drawRect(bounds, outline)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val dotOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * densityScale
        }
        for (zone in analysis.borderZones) {
            dot.color = when (zone.color) {
                CardBorderColor.BLACK -> Color.rgb(25, 25, 25)
                CardBorderColor.WHITE -> Color.rgb(245, 245, 238)
                CardBorderColor.GOLD -> Color.rgb(218, 166, 61)
                CardBorderColor.SILVER -> Color.rgb(160, 166, 172)
                CardBorderColor.FULL_ART -> Color.rgb(70, 205, 219)
                CardBorderColor.MIXED -> Color.rgb(179, 95, 220)
                CardBorderColor.UNKNOWN -> Color.rgb(239, 88, 88)
            }
            val x = zone.x * scale
            val y = zone.y * scale
            canvas.drawCircle(x, y, 7f * densityScale, dot)
            canvas.drawCircle(x, y, 7f * densityScale, dotOutline)
        }
        return preview
    }

    internal fun classifyRgb(red: Int, green: Int, blue: Int): CardBorderColor {
        val high = maxOf(red, green, blue)
        val low = minOf(red, green, blue)
        val chroma = high - low
        val luma = (red * 299 + green * 587 + blue * 114) / 1000
        return when {
            luma <= 92 -> CardBorderColor.BLACK
            luma >= 157 && chroma <= 70 -> CardBorderColor.WHITE
            luma in 92..185 && chroma <= 38 -> CardBorderColor.SILVER
            luma in 75..215 && red > green + 5 && green > blue + 4 && red - blue >= 25 ->
                CardBorderColor.GOLD
            else -> CardBorderColor.UNKNOWN
        }
    }

    internal fun classifyBorderZones(zones: List<CardBorderZone>): Pair<CardBorderColor, Double> {
        if (zones.isEmpty()) return CardBorderColor.UNKNOWN to 0.0
        val known = zones.filter { it.color != CardBorderColor.UNKNOWN }
        val counts = known.groupingBy { it.color }.eachCount().entries.sortedByDescending { it.value }
        val first = counts.firstOrNull()
        val second = counts.getOrNull(1)
        val knownRatio = known.size / zones.size.toDouble()
        val winnerRatio = (first?.value ?: 0) / zones.size.toDouble()
        val secondRatio = (second?.value ?: 0) / zones.size.toDouble()
        val unknownRatio = 1.0 - knownRatio
        val dispersion = rgbDispersion(zones)
        val chromaticUnknownRatio = zones.count { zone ->
            zone.color == CardBorderColor.UNKNOWN &&
                maxOf(zone.red, zone.green, zone.blue) - minOf(zone.red, zone.green, zone.blue) >= 42
        } / zones.size.toDouble()

        // A real printed border is deliberately uniform. Borderless/full-art cards instead expose
        // unrelated illustration colours around the four sides. Use the raw samples, not only the
        // coarse colour labels, so dark full-art edges do not become a false black border.
        val looksBorderless = dispersion >= .18 ||
            chromaticUnknownRatio >= .50 ||
            (dispersion >= .10 && unknownRatio >= .38) ||
            (dispersion >= .13 && winnerRatio < .62)
        if (looksBorderless) {
            val confidence = maxOf(
                dispersion / .30,
                chromaticUnknownRatio,
                unknownRatio * .85
            ).coerceIn(.45, .95)
            return CardBorderColor.FULL_ART to confidence
        }
        if (known.size < zones.size * .45 || first == null) {
            return CardBorderColor.UNKNOWN to knownRatio
        }
        if (secondRatio >= .25 && winnerRatio - secondRatio < .20) {
            return CardBorderColor.MIXED to ((winnerRatio + secondRatio) * knownRatio).coerceIn(.35, .90)
        }
        return first.key to (winnerRatio * .75 + knownRatio * .25).coerceIn(.0, 1.0)
    }

    private fun rgbDispersion(zones: List<CardBorderZone>): Double {
        if (zones.size < 2) return 0.0
        fun median(values: List<Int>): Int = values.sorted()[values.size / 2]
        val medianRed = median(zones.map(CardBorderZone::red))
        val medianGreen = median(zones.map(CardBorderZone::green))
        val medianBlue = median(zones.map(CardBorderZone::blue))
        return zones.map { zone ->
            val red = zone.red - medianRed
            val green = zone.green - medianGreen
            val blue = zone.blue - medianBlue
            sqrt((red * red + green * green + blue * blue).toDouble()) / MAX_RGB_DISTANCE
        }.average().coerceIn(0.0, 1.0)
    }

    private data class Edge(val coordinate: Int, val confidence: Double)

    private fun strongestVerticalEdge(
        bitmap: Bitmap,
        expectedX: Int,
        radius: Int,
        top: Int,
        bottom: Int,
        offset: Int
    ): Edge {
        val start = (expectedX - radius).coerceIn(offset, bitmap.width - offset - 1)
        val end = (expectedX + radius).coerceIn(start, bitmap.width - offset - 1)
        val yStart = (top + (bottom - top) * .10f).toInt().coerceAtLeast(0)
        val yEnd = (bottom - (bottom - top) * .10f).toInt().coerceAtMost(bitmap.height - 1)
        val yStep = max(2, (yEnd - yStart) / 90)
        var bestX = expectedX.coerceIn(start, end)
        var best = 0.0
        var x = start
        while (x <= end) {
            var total = 0.0
            var count = 0
            var y = yStart
            while (y <= yEnd) {
                total += colorDistance(bitmap.getPixel(x - offset, y), bitmap.getPixel(x + offset, y))
                count++
                y += yStep
            }
            val score = if (count == 0) 0.0 else total / count
            if (score > best) {
                best = score
                bestX = x
            }
            x += max(1, bitmap.width / 700)
        }
        return Edge(bestX, (best / 105.0).coerceIn(0.0, 1.0))
    }

    private fun strongestHorizontalEdge(
        bitmap: Bitmap,
        expectedY: Int,
        radius: Int,
        left: Int,
        right: Int,
        offset: Int
    ): Edge {
        val start = (expectedY - radius).coerceIn(offset, bitmap.height - offset - 1)
        val end = (expectedY + radius).coerceIn(start, bitmap.height - offset - 1)
        val xStart = (left + (right - left) * .10f).toInt().coerceAtLeast(0)
        val xEnd = (right - (right - left) * .10f).toInt().coerceAtMost(bitmap.width - 1)
        val xStep = max(2, (xEnd - xStart) / 65)
        var bestY = expectedY.coerceIn(start, end)
        var best = 0.0
        var y = start
        while (y <= end) {
            var total = 0.0
            var count = 0
            var x = xStart
            while (x <= xEnd) {
                total += colorDistance(bitmap.getPixel(x, y - offset), bitmap.getPixel(x, y + offset))
                count++
                x += xStep
            }
            val score = if (count == 0) 0.0 else total / count
            if (score > best) {
                best = score
                bestY = y
            }
            y += max(1, bitmap.height / 900)
        }
        return Edge(bestY, (best / 105.0).coerceIn(0.0, 1.0))
    }

    private fun sampleBorderZones(bitmap: Bitmap, card: Rect): List<CardBorderZone> {
        val inset = max(2, (min(card.width(), card.height()) * .027f).toInt())
        val patchRadius = max(1, min(card.width(), card.height()) / 420)
        val zones = ArrayList<CardBorderZone>(samplePositions.size * 4)
        for (position in samplePositions) {
            val x = (card.left + card.width() * position).toInt().coerceIn(0, bitmap.width - 1)
            val y = (card.top + card.height() * position).toInt().coerceIn(0, bitmap.height - 1)
            zones += zone(bitmap, CardBorderSide.TOP, position, x, card.top + inset, patchRadius)
            zones += zone(bitmap, CardBorderSide.BOTTOM, position, x, card.bottom - inset - 1, patchRadius)
            zones += zone(bitmap, CardBorderSide.LEFT, position, card.left + inset, y, patchRadius)
            zones += zone(bitmap, CardBorderSide.RIGHT, position, card.right - inset - 1, y, patchRadius)
        }
        return zones
    }

    private fun zone(
        bitmap: Bitmap,
        side: CardBorderSide,
        position: Float,
        x: Int,
        y: Int,
        radius: Int
    ): CardBorderZone {
        val pixels = ArrayList<Int>((radius * 2 + 1) * (radius * 2 + 1))
        for (sampleY in (y - radius).coerceAtLeast(0)..(y + radius).coerceAtMost(bitmap.height - 1)) {
            for (sampleX in (x - radius).coerceAtLeast(0)..(x + radius).coerceAtMost(bitmap.width - 1)) {
                pixels += bitmap.getPixel(sampleX, sampleY)
            }
        }
        fun median(channel: (Int) -> Int): Int = pixels.map(channel).sorted()[pixels.size / 2]
        val red = median { Color.red(it) }
        val green = median { Color.green(it) }
        val blue = median { Color.blue(it) }
        return CardBorderZone(
            side, position, x, y, red, green, blue, classifyRgb(red, green, blue)
        )
    }

    private fun glareRatio(bitmap: Bitmap, card: Rect): Double {
        var bright = 0
        var total = 0
        val xStep = max(2, card.width() / 55)
        val yStep = max(2, card.height() / 78)
        var y = card.top
        while (y < card.bottom) {
            var x = card.left
            while (x < card.right) {
                val color = bitmap.getPixel(x, y)
                val high = maxOf(Color.red(color), Color.green(color), Color.blue(color))
                val low = minOf(Color.red(color), Color.green(color), Color.blue(color))
                if (high >= 247 && high - low <= 16) bright++
                total++
                x += xStep
            }
            y += yStep
        }
        return if (total == 0) 0.0 else bright / total.toDouble()
    }

    private fun sharpness(bitmap: Bitmap, card: Rect): Double {
        var total = 0.0
        var count = 0
        val xStep = max(2, card.width() / 70)
        val yStep = max(2, card.height() / 90)
        var y = card.top + yStep
        while (y < card.bottom - yStep) {
            var x = card.left + xStep
            while (x < card.right - xStep) {
                val center = luma(bitmap.getPixel(x, y))
                val laplacian = abs(
                    4 * center - luma(bitmap.getPixel(x - xStep, y)) -
                        luma(bitmap.getPixel(x + xStep, y)) -
                        luma(bitmap.getPixel(x, y - yStep)) -
                        luma(bitmap.getPixel(x, y + yStep))
                )
                total += laplacian
                count++
                x += xStep
            }
            y += yStep
        }
        return if (count == 0) 0.0 else (total / count / 85.0).coerceIn(0.0, 1.0)
    }

    private fun colorDistance(left: Int, right: Int): Double {
        val red = Color.red(left) - Color.red(right)
        val green = Color.green(left) - Color.green(right)
        val blue = Color.blue(left) - Color.blue(right)
        return sqrt((red * red + green * green + blue * blue).toDouble())
    }

    private fun luma(color: Int): Int =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000

    private const val CARD_ASPECT = 63.0 / 88.0
    private const val MAX_RGB_DISTANCE = 441.67295593
}
