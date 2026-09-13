package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max

/** Border colours used by physical Magic printings. */
enum class CardBorderColor {
    BLACK,
    WHITE,
    GOLD,
    SILVER,
    MIXED,
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
        fun fromCamera(bitmap: Bitmap, card: Rect = CardImageFingerprint.centeredCardRect(
            bitmap.width, bitmap.height, .72f
        )): CardEditionVisualFingerprint = fromCard(bitmap, card, true)

        /** Exact camera crop used for the set-symbol comparison, exposed for visible diagnostics. */
        fun setSymbolCropFromCamera(
            bitmap: Bitmap,
            card: Rect = CardImageFingerprint.centeredCardRect(bitmap.width, bitmap.height, .72f)
        ): Bitmap {
            val symbol = setSymbolRect(bitmap, card)
            return Bitmap.createBitmap(bitmap, symbol.left, symbol.top, symbol.width(), symbol.height())
        }

        /** The dedicated scanner asks the user to place only the printed set symbol in the centre. */
        fun setSymbolCropFromGuide(bitmap: Bitmap): Bitmap {
            val size = (minOf(bitmap.width, bitmap.height) * .22f).toInt().coerceAtLeast(1)
            val left = ((bitmap.width - size) / 2).coerceAtLeast(0)
            val top = ((bitmap.height - size) / 2).coerceAtLeast(0)
            return Bitmap.createBitmap(bitmap, left, top, size.coerceAtMost(bitmap.width - left),
                size.coerceAtMost(bitmap.height - top))
        }

        fun setSymbolHashFromGuide(bitmap: Bitmap): LongArray {
            val size = (minOf(bitmap.width, bitmap.height) * .22f).toInt().coerceAtLeast(1)
            val left = ((bitmap.width - size) / 2).coerceAtLeast(0)
            val top = ((bitmap.height - size) / 2).coerceAtLeast(0)
            val crop = Rect(left, top, (left + size).coerceAtMost(bitmap.width),
                (top + size).coerceAtMost(bitmap.height))
            return differenceHash(bitmap, crop)
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
            val zones = samples.mapIndexed { index, pixel ->
                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff
                CardBorderZone(
                    CardBorderSide.TOP,
                    index / samples.size.toFloat(),
                    index,
                    0,
                    red,
                    green,
                    blue,
                    CardFrameAnalyzer.classifyRgb(red, green, blue)
                )
            }
            return CardFrameAnalyzer.classifyBorderZones(zones)
        }

        private fun fromCard(bitmap: Bitmap, card: Rect, camera: Boolean): CardEditionVisualFingerprint {
            val symbol = setSymbolRect(bitmap, card)
            val border = borderSamples(bitmap, card, camera)
            val classifiedBorder = classifyBorder(border)
            return CardEditionVisualFingerprint(
                artworkHash = if (camera) CardImageFingerprint.fromCamera(bitmap, card)
                    else CardImageFingerprint.fromReference(bitmap),
                setSymbolHash = differenceHash(bitmap, symbol),
                borderColor = classifiedBorder.first,
                borderConfidence = classifiedBorder.second
            )
        }

        private fun setSymbolRect(bitmap: Bitmap, card: Rect): Rect =
            relativeRect(bitmap, card, .81f, .515f, .96f, .61f)

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
            // Sample only the actual printed border. The previous inner sample reached the brown
            // card frame, which made black-bordered old cards look gold under warm lighting.
            val insetFractions = if (camera) floatArrayOf(.026f, .038f, .050f)
                else floatArrayOf(.012f, .022f, .032f)
            val points = ArrayList<Int>(512)
            val horizontalStep = max(1, card.width() / 80)
            val verticalStep = max(1, card.height() / 100)
            val offsets = insetFractions.map { max(1, (card.width() * it).toInt()) }

            var x = card.left + card.width() / 8
            while (x < card.right - card.width() / 8) {
                for (offset in offsets) {
                    addPixel(points, bitmap, x, card.top + offset)
                    addPixel(points, bitmap, x, card.bottom - offset - 1)
                }
                x += horizontalStep
            }
            var y = card.top + card.height() / 8
            while (y < card.bottom - card.height() / 8) {
                for (offset in offsets) {
                    addPixel(points, bitmap, card.left + offset, y)
                    addPixel(points, bitmap, card.right - offset - 1, y)
                }
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

        private const val ARTWORK_WEIGHT = .45
        private const val SYMBOL_WEIGHT = .40
        private const val BORDER_WEIGHT = .15
    }
}
