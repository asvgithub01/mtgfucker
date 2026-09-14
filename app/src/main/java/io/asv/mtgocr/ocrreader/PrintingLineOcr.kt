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
    val preview: Bitmap,
    val successfulVariants: Int
)

/** Experimental OCR pass over the normalized lower band, with no OpenCV dependency yet. */
class PrintingLineOcr {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognize(
        card: Bitmap,
        callback: (PrintingLineOcrResult?, Throwable?) -> Unit
    ) {
        val crop = lowerBand(card)
        val enlarged = enlarge(crop)
        if (crop !== enlarged) crop.recycle()
        val contrasted = autoContrast(enlarged)
        val thresholded = otsuThreshold(contrasted)
        val variants = listOf(enlarged, contrasted, thresholded)
        val tasks = variants.map { recognizer.process(InputImage.fromBitmap(it, 0)) }
        Tasks.whenAllComplete(tasks).addOnCompleteListener {
            val lines = LinkedHashSet<String>()
            var successful = 0
            tasks.forEach { task ->
                if (task.isSuccessful) {
                    successful++
                    task.result?.textBlocks
                        ?.flatMap { it.lines }
                        ?.sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        ?.map { it.text.trim() }
                        ?.filter(String::isNotBlank)
                        ?.forEach(lines::add)
                }
            }
            contrasted.recycle()
            thresholded.recycle()
            if (successful == 0) {
                enlarged.recycle()
                callback(null, tasks.firstNotNullOfOrNull { it.exception }
                    ?: IllegalStateException("No se pudo ejecutar OCR"))
            } else {
                callback(
                    PrintingLineOcrResult(
                        rawText = lines.joinToString("\n"),
                        lines = lines.toList(),
                        preview = enlarged,
                        successfulVariants = successful
                    ),
                    null
                )
            }
        }
    }

    fun close() = recognizer.close()

    private fun lowerBand(card: Bitmap): Bitmap {
        val left = (card.width * .015f).toInt().coerceIn(0, card.width - 1)
        val right = (card.width * .985f).toInt().coerceIn(left + 1, card.width)
        val top = (card.height * .74f).toInt().coerceIn(0, card.height - 1)
        val bottom = (card.height * .995f).toInt().coerceIn(top + 1, card.height)
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
    }
}
