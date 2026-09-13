package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max

/** Border colours used by physical Magic printings. */
enum class CardBorderColor {
    BLACK,
    WHITE,
    GOLD,
    UNKNOWN
}

/** Visual evidence used to distinguish editions that share a name or illustration. */
data class CardEditionVisualFingerprint(
    val artworkHash: LongArray,
    val setSymbolHash: LongArray,
    val borderColor: CardBorderColor,
    val borderConfidence: Double
) {
    companion object {
        private const val SYMBOL_HASH_WIDTH = 16
        private const val SYMBOL_HASH_HEIGHT = 16

        /** Camera photos contain a card centred inside the scanner guide. */
        fun fromCamera(bitmap: Bitmap): CardEditionVisualFingerprint =
            fromCard(bitmap, CardImageFingerprint.centeredCardRect(bitmap.width, bitmap.height, .72f), true)

        /** Exact camera crop used for the set-symbol comparison, exposed for visible diagnostics. */
        fun setSymbolCropFromCamera(bitmap: Bitmap): Bitmap {
            val card = CardImageFingerprint.centeredCardRect(bitmap.width, bitmap.height, .72f)
            val symbol = setSymbolRect(bitmap, card)
            return Bitmap.createBitmap(bitmap, symbol.left, symbol.top, symbol.width(), symbol.height())
        }

        /** Scryfall images are already tightly cropped to the complete card. */
        fun fromReference(bitmap: Bitmap): CardEditionVisualFingerprint =
            fromCard(bitmap, Rect(0, 0, bitmap.width, bitmap.height), false)

        /**
         * Combines the illustration with the two edition-specific signals. The illustration keeps
         * most of the weight because it is much more tolerant of perspective and unusual frames.
         */
        fun combinedDistance(
            artworkDistance: Double,
            symbolDistance: Double,
            cameraBorder: CardBorderColor,
            referenceBorder: CardBorderColor,
            cameraBorderConfidence: Double = 1.0,
            referenceBorderConfidence: Double = 1.0
        ): Double {
            val borderDistance = when {
                cameraBorder == CardBorderColor.UNKNOWN || referenceBorder == CardBorderColor.UNKNOWN -> .5
                cameraBorder == referenceBorder -> 0.0
                else -> 1.0
            }
            val reliableBorderWeight = BORDER_WEIGHT *
                minOf(cameraBorderConfidence, referenceBorderConfidence).coerceIn(0.0, 1.0)
            val neutralBorderWeight = BORDER_WEIGHT - reliableBorderWeight
            return (
                artworkDistance.coerceIn(0.0, 1.0) * ARTWORK_WEIGHT +
                    symbolDistance.coerceIn(0.0, 1.0) * SYMBOL_WEIGHT +
                    borderDistance * reliableBorderWeight +
                    .5 * neutralBorderWeight
                ).coerceIn(0.0, 1.0)
        }

        /** Kept separate from Bitmap access so the colour decision remains JVM-testable. */
        internal fun classifyBorder(samples: IntArray): Pair<CardBorderColor, Double> {
            if (samples.isEmpty()) return CardBorderColor.UNKNOWN to 0.0
            var black = 0
            var white = 0
            var gold = 0
            var redTotal = 0L
            var greenTotal = 0L
            var blueTotal = 0L
            for (pixel in samples) {
                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff
                val high = maxOf(red, green, blue)
                val low = minOf(red, green, blue)
                val luma = (red * 299 + green * 587 + blue * 114) / 1000
                redTotal += red
                greenTotal += green
                blueTotal += blue
                if (luma <= 78) black++
                if (luma >= 165 && high - low <= 72) white++
                if (red >= 60 && red - green in 4..92 && green - blue >= 5 && high - low >= 18) gold++
            }
            val size = samples.size.toDouble()
            val blackRatio = black / size
            val whiteRatio = white / size
            val goldRatio = gold / size
            val averageRed = redTotal / size
            val averageGreen = greenTotal / size
            val averageBlue = blueTotal / size
            val averageLooksGold = averageRed > averageGreen + 8 &&
                averageGreen > averageBlue + 7 && averageRed - averageBlue > 28

            return when {
                goldRatio >= .28 || (goldRatio >= .18 && averageLooksGold) ->
                    CardBorderColor.GOLD to ((goldRatio - .18) / .82).coerceIn(.35, 1.0)
                blackRatio >= .42 && blackRatio >= whiteRatio && blackRatio >= goldRatio ->
                    CardBorderColor.BLACK to ((blackRatio - .42) / .58).coerceIn(.35, 1.0)
                whiteRatio >= .38 && whiteRatio > goldRatio * 1.15 ->
                    CardBorderColor.WHITE to ((whiteRatio - .38) / .62).coerceIn(.35, 1.0)
                else -> CardBorderColor.UNKNOWN to maxOf(blackRatio, whiteRatio, goldRatio)
            }
        }

        private fun fromCard(bitmap: Bitmap, card: Rect, camera: Boolean): CardEditionVisualFingerprint {
            val symbol = setSymbolRect(bitmap, card)
            val border = borderSamples(bitmap, card, camera)
            val classifiedBorder = classifyBorder(border)
            return CardEditionVisualFingerprint(
                artworkHash = if (camera) CardImageFingerprint.fromCamera(bitmap)
                    else CardImageFingerprint.fromReference(bitmap),
                setSymbolHash = differenceHash(bitmap, symbol),
                borderColor = classifiedBorder.first,
                borderConfidence = classifiedBorder.second
            )
        }

        private fun setSymbolRect(bitmap: Bitmap, card: Rect): Rect =
            relativeRect(bitmap, card, .70f, .515f, .945f, .635f)

        private fun relativeRect(
            bitmap: Bitmap,
            card: Rect,
            leftFraction: Float,
            topFraction: Float,
            rightFraction: Float,
            bottomFraction: Float
        ): Rect {
            val left = (card.left + card.width() * leftFraction).toInt().coerceIn(0, bitmap.width - 1)
            val top = (card.top + card.height() * topFraction).toInt().coerceIn(0, bitmap.height - 1)
            val right = (card.left + card.width() * rightFraction).toInt().coerceIn(left + 1, bitmap.width)
            val bottom = (card.top + card.height() * bottomFraction).toInt().coerceIn(top + 1, bitmap.height)
            return Rect(left, top, right, bottom)
        }

        private fun borderSamples(bitmap: Bitmap, card: Rect, camera: Boolean): IntArray {
            // Stay inside the printed edge. A slightly wider inset for camera photos avoids
            // sampling the dark preview around a card that is not aligned to the exact pixel.
            val inner = if (camera) .070f else .055f
            val outer = if (camera) .025f else .014f
            val points = ArrayList<Int>(512)
            val horizontalStep = max(1, card.width() / 80)
            val verticalStep = max(1, card.height() / 100)
            val topOuter = card.top + (card.height() * outer).toInt()
            val topInner = card.top + (card.height() * inner).toInt()
            val bottomOuter = card.bottom - (card.height() * outer).toInt() - 1
            val bottomInner = card.bottom - (card.height() * inner).toInt() - 1
            val leftOuter = card.left + (card.width() * outer).toInt()
            val leftInner = card.left + (card.width() * inner).toInt()
            val rightOuter = card.right - (card.width() * outer).toInt() - 1
            val rightInner = card.right - (card.width() * inner).toInt() - 1

            var x = card.left + card.width() / 8
            while (x < card.right - card.width() / 8) {
                addPixel(points, bitmap, x, topOuter)
                addPixel(points, bitmap, x, topInner)
                addPixel(points, bitmap, x, bottomOuter)
                addPixel(points, bitmap, x, bottomInner)
                x += horizontalStep
            }
            var y = card.top + card.height() / 8
            while (y < card.bottom - card.height() / 8) {
                addPixel(points, bitmap, leftOuter, y)
                addPixel(points, bitmap, leftInner, y)
                addPixel(points, bitmap, rightOuter, y)
                addPixel(points, bitmap, rightInner, y)
                y += verticalStep
            }
            return points.toIntArray()
        }

        private fun addPixel(points: MutableList<Int>, bitmap: Bitmap, x: Int, y: Int) {
            if (x in 0 until bitmap.width && y in 0 until bitmap.height) points += bitmap.getPixel(x, y)
        }

        private fun differenceHash(bitmap: Bitmap, crop: Rect): LongArray {
            val cropped = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
            val scaled = Bitmap.createScaledBitmap(cropped, SYMBOL_HASH_WIDTH + 1, SYMBOL_HASH_HEIGHT, true)
            if (scaled !== cropped) cropped.recycle()
            val words = LongArray((SYMBOL_HASH_WIDTH * SYMBOL_HASH_HEIGHT + 63) / 64)
            var bit = 0
            for (y in 0 until SYMBOL_HASH_HEIGHT) {
                for (x in 0 until SYMBOL_HASH_WIDTH) {
                    if (luma(scaled.getPixel(x, y)) > luma(scaled.getPixel(x + 1, y))) {
                        words[bit / 64] = words[bit / 64] or (1L shl (bit % 64))
                    }
                    bit++
                }
            }
            scaled.recycle()
            return words
        }

        private fun luma(color: Int): Int =
            ((color shr 16 and 0xff) * 299 + (color shr 8 and 0xff) * 587 + (color and 0xff) * 114) / 1000

        private const val ARTWORK_WEIGHT = .62
        private const val SYMBOL_WEIGHT = .30
        private const val BORDER_WEIGHT = .08
    }
}
