package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/** Difference-hash over the artwork band. It is intentionally small and robust to camera noise. */
object CardImageFingerprint {
    const val HASH_WIDTH = 16
    const val HASH_HEIGHT = 16
    const val BIT_COUNT = HASH_WIDTH * HASH_HEIGHT

    /** Camera photos contain the card centered inside the on-screen guide. */
    fun fromCamera(bitmap: Bitmap): LongArray {
        val card = centeredCardRect(bitmap.width, bitmap.height, 0.72f)
        return fromCard(bitmap, card)
    }

    /** Catalog images are already tightly cropped to the complete card. */
    fun fromReference(bitmap: Bitmap): LongArray =
        fromCard(bitmap, Rect(0, 0, bitmap.width, bitmap.height))

    fun normalizedDistance(left: LongArray, right: LongArray): Double {
        if (left.size != right.size || left.isEmpty()) return 1.0
        var changed = 0
        for (index in left.indices) changed += java.lang.Long.bitCount(left[index] xor right[index])
        return changed.toDouble() / BIT_COUNT.toDouble()
    }

    internal fun centeredCardRect(width: Int, height: Int, heightFraction: Float): Rect {
        val cardHeight = max(1, (height * heightFraction.coerceIn(.35f, .95f)).toInt())
        val cardWidth = max(1, (cardHeight * CARD_ASPECT_RATIO).toInt())
        val fittedWidth: Int
        val fittedHeight: Int
        if (cardWidth <= width) {
            fittedWidth = cardWidth
            fittedHeight = cardHeight
        } else {
            fittedWidth = max(1, (width * .9f).toInt())
            fittedHeight = max(1, (fittedWidth / CARD_ASPECT_RATIO).toInt())
        }
        val left = max(0, (width - fittedWidth) / 2)
        val top = max(0, (height - fittedHeight) / 2)
        return Rect(left, top, min(width, left + fittedWidth), min(height, top + fittedHeight))
    }

    private fun fromCard(bitmap: Bitmap, card: Rect): LongArray {
        // This common band contains the illustration on normal, showcase and full-art frames.
        val left = card.left + (card.width() * .07f).toInt()
        val right = card.left + (card.width() * .93f).toInt()
        val top = card.top + (card.height() * .16f).toInt()
        val bottom = card.top + (card.height() * .54f).toInt()
        val art = Rect(
            left.coerceIn(0, bitmap.width - 1),
            top.coerceIn(0, bitmap.height - 1),
            right.coerceIn(1, bitmap.width),
            bottom.coerceIn(1, bitmap.height)
        )
        val sampleWidth = HASH_WIDTH + 1
        val cropped = Bitmap.createBitmap(bitmap, art.left, art.top, max(1, art.width()), max(1, art.height()))
        val scaled = Bitmap.createScaledBitmap(cropped, sampleWidth, HASH_HEIGHT, true)
        if (scaled !== cropped) cropped.recycle()
        val words = LongArray((BIT_COUNT + 63) / 64)
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH) {
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

    private const val CARD_ASPECT_RATIO = 63f / 88f
}
