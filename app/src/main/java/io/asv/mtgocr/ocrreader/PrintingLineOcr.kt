package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.LinkedHashSet
import kotlin.math.max
import kotlin.math.min

data class PrintingLineOcrResult(
    val rawText: String,
    val lines: List<String>,
    val fullCardText: String,
    val preview: Bitmap,
    val successfulVariants: Int,
    val attemptedVariants: Int
)

/** Multi-crop OCR pass over the tiny printing metadata in a normalized card. */
class PrintingLineOcr {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognize(
        card: Bitmap,
        callback: (PrintingLineOcrResult?, Throwable?) -> Unit
    ) {
        val wideCrop = crop(card, .015f, .70f, .985f, .995f)
        val focusedCrop = crop(card, .015f, .82f, .78f, .995f)
        val wide = enlarge(wideCrop)
        val focused = enlarge(focusedCrop)
        wideCrop.recycle()
        focusedCrop.recycle()
        val wideContrast = autoContrast(wide)
        val focusedContrast = autoContrast(focused)
        val fullCard = enlargeFullCard(card)
        val variants = listOf(
            OcrVariant(wide, yearOnly = false),
            OcrVariant(wideContrast, yearOnly = false),
            OcrVariant(otsuThreshold(wideContrast), yearOnly = false),
            OcrVariant(focused, yearOnly = false),
            OcrVariant(focusedContrast, yearOnly = false),
            OcrVariant(otsuThreshold(focusedContrast), yearOnly = false),
            // Old cards often expose the copyright year outside the modern collector-number band.
            // Read the complete rectified card but only retain lines containing a plausible year,
            // otherwise rules text would pollute the printing-token parser.
            OcrVariant(fullCard, yearOnly = true)
        )
        val tasks = variants.map { recognizer.process(InputImage.fromBitmap(it.bitmap, 0)) }
        Tasks.whenAllComplete(tasks).addOnCompleteListener {
            val lines = LinkedHashSet<String>()
            var fullCardText = ""
            var successful = 0
            tasks.forEachIndexed { index, task ->
                if (task.isSuccessful) {
                    successful++
                    if (variants[index].yearOnly) {
                        fullCardText = task.result?.text.orEmpty()
                    }
                    task.result?.textBlocks
                        ?.flatMap { it.lines }
                        ?.sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        ?.map { it.text.trim() }
                        ?.filter(String::isNotBlank)
                        ?.filter { !variants[index].yearOnly || YEAR_PATTERN.containsMatchIn(it) }
                        ?.forEach(lines::add)
                }
            }
            variants.map(OcrVariant::bitmap).filter { it !== wide }.forEach(Bitmap::recycle)
            if (successful == 0) {
                wide.recycle()
                callback(null, tasks.firstNotNullOfOrNull { it.exception }
                    ?: IllegalStateException("No se pudo ejecutar OCR"))
            } else {
                callback(
                    PrintingLineOcrResult(
                        rawText = lines.joinToString("\n"),
                        lines = lines.toList(),
                        fullCardText = fullCardText,
                        preview = wide,
                        successfulVariants = successful,
                        attemptedVariants = variants.size
                    ),
                    null
                )
            }
        }
    }

    fun close() = recognizer.close()

    private fun crop(
        card: Bitmap,
        leftRatio: Float,
        topRatio: Float,
        rightRatio: Float,
        bottomRatio: Float
    ): Bitmap {
        val left = (card.width * leftRatio).toInt().coerceIn(0, card.width - 1)
        val right = (card.width * rightRatio).toInt().coerceIn(left + 1, card.width)
        val top = (card.height * topRatio).toInt().coerceIn(0, card.height - 1)
        val bottom = (card.height * bottomRatio).toInt().coerceIn(top + 1, card.height)
        return Bitmap.createBitmap(card, left, top, right - left, bottom - top)
    }

    private fun enlarge(source: Bitmap): Bitmap {
        val scale = min(3f, MAX_WIDTH / source.width.toFloat()).coerceAtLeast(1f)
        if (scale <= 1f) return source.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(
            source,
            max(1, (source.width * scale).toInt()),
            max(1, (source.height * scale).toInt()),
            true
        )
    }

    private fun enlargeFullCard(source: Bitmap): Bitmap {
        val scale = min(2f, FULL_CARD_WIDTH / source.width.toFloat()).coerceAtLeast(1f)
        if (scale <= 1f) return source.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(
            source,
            max(1, (source.width * scale).toInt()),
            max(1, (source.height * scale).toInt()),
            true
        )
    }

    private fun autoContrast(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val histogram = IntArray(256)
        pixels.forEach { histogram[luma(it)]++ }
        val clip = max(1, pixels.size / 100)
        var low = 0
        var accumulated = 0
        while (low < 255 && accumulated + histogram[low] < clip) {
            accumulated += histogram[low++]
        }
        var high = 255
        accumulated = 0
        while (high > low && accumulated + histogram[high] < clip) {
            accumulated += histogram[high--]
        }
        val range = max(1, high - low)
        for (index in pixels.indices) {
            val gray = ((luma(pixels[index]) - low) * 255 / range).coerceIn(0, 255)
            pixels[index] = Color.rgb(gray, gray, gray)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun otsuThreshold(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val histogram = IntArray(256)
        pixels.forEach { histogram[luma(it)]++ }
        val threshold = otsu(histogram, pixels.size)
        for (index in pixels.indices) {
            val value = if (luma(pixels[index]) >= threshold) 255 else 0
            pixels[index] = Color.rgb(value, value, value)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun otsu(histogram: IntArray, total: Int): Int {
        var sum = 0L
        histogram.indices.forEach { sum += it.toLong() * histogram[it] }
        var backgroundWeight = 0
        var backgroundSum = 0L
        var bestVariance = -1.0
        var bestThreshold = 128
        for (value in histogram.indices) {
            backgroundWeight += histogram[value]
            if (backgroundWeight == 0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0) break
            backgroundSum += value.toLong() * histogram[value]
            val backgroundMean = backgroundSum.toDouble() / backgroundWeight
            val foregroundMean = (sum - backgroundSum).toDouble() / foregroundWeight
            val variance = backgroundWeight.toDouble() * foregroundWeight *
                (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = value
            }
        }
        return bestThreshold
    }

    private fun luma(color: Int): Int =
        ((color shr 16 and 0xff) * 299 + (color shr 8 and 0xff) * 587 +
            (color and 0xff) * 114) / 1000

    private companion object {
        const val MAX_WIDTH = 1_800f
        const val FULL_CARD_WIDTH = 1_200f
        // Copyright text is tiny: OCR commonly turns 1 into I/l and 0 into O.
        val YEAR_PATTERN = Regex("(?i)(?<![a-z0-9])(?:[1il][9o]|2[0o])[0-9oil]{2}(?![a-z0-9])")
    }

    private data class OcrVariant(val bitmap: Bitmap, val yearOnly: Boolean)
}
