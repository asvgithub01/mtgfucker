package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.LinkedHashSet
import kotlin.math.max
import kotlin.math.min

data class CardTitleOcrResult(
    val lines: List<String>,
    val successfulVariants: Int,
    val attemptedVariants: Int
)

/** Reads the printed card name first, using contrast variants suited to pale or reflective frames. */
class CardTitleOcr {
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val japaneseRecognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    fun recognize(card: Bitmap, enhanced: Boolean = false, callback: (CardTitleOcrResult?, Throwable?) -> Unit) {
        val titleCrop = crop(card, .025f, .012f, .91f, .145f)
        val headerCrop = crop(card, .012f, .005f, .985f, .175f)
        val title = enlarge(titleCrop)
        val header = enlarge(headerCrop)
        titleCrop.recycle()
        headerCrop.recycle()
        val variants = mutableListOf(
            title,
            autoContrast(title),
            adaptiveThreshold(title),
            header,
            autoContrast(header),
            adaptiveThreshold(header)
        )
        if (enhanced) variants += OcrImageEnhancement.clahe(title)
        val latinTasks = variants.map { latinRecognizer.process(InputImage.fromBitmap(it, 0)) }
        val japaneseTasks = listOf(title, variants[1], header, variants[4]).map {
            japaneseRecognizer.process(InputImage.fromBitmap(it, 0))
        }
        val tasks = latinTasks + japaneseTasks
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
                        ?.filter { value ->
                            value.length in 2..60 && value.any(Char::isLetter) &&
                                value.count(Char::isDigit) * 2 < value.length
                        }
                        ?.forEach(lines::add)
                }
            }
            variants.forEach(Bitmap::recycle)
            if (successful == 0) {
                callback(
                    null,
                    tasks.firstNotNullOfOrNull { it.exception }
                        ?: IllegalStateException("No se pudo leer el nombre")
                )
            } else {
                callback(CardTitleOcrResult(lines.toList(), successful, tasks.size), null)
            }
        }
    }

    fun close() {
        latinRecognizer.close()
        japaneseRecognizer.close()
    }

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
        val scale = min(4f, MAX_WIDTH / source.width.toFloat()).coerceAtLeast(1f)
        return Bitmap.createScaledBitmap(
            source,
            max(1, (source.width * scale).toInt()),
            max(1, (source.height * scale).toInt()),
            true
        )
    }

    private fun autoContrast(source: Bitmap): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val histogram = IntArray(256)
        pixels.forEach { histogram[luma(it)]++ }
        val clip = max(1, pixels.size / 100)
        var low = 0
        var accumulated = 0
        while (low < 255 && accumulated + histogram[low] < clip) accumulated += histogram[low++]
        var high = 255
        accumulated = 0
        while (high > low && accumulated + histogram[high] < clip) accumulated += histogram[high--]
        val range = max(1, high - low)
        pixels.indices.forEach { index ->
            val gray = ((luma(pixels[index]) - low) * 255 / range).coerceIn(0, 255)
            pixels[index] = Color.rgb(gray, gray, gray)
        }
        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        }
    }

    /** Local mean threshold keeps dark title glyphs visible when glare flattens global contrast. */
    private fun adaptiveThreshold(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += luma(pixels[y * width + x])
                integral[(y + 1) * (width + 1) + x + 1] =
                    integral[y * (width + 1) + x + 1] + rowSum
            }
        }
        val radius = max(8, min(width, height) / 18)
        for (y in 0 until height) {
            val top = max(0, y - radius)
            val bottom = min(height, y + radius + 1)
            for (x in 0 until width) {
                val left = max(0, x - radius)
                val right = min(width, x + radius + 1)
                val area = (right - left) * (bottom - top)
                val sum = integral[bottom * (width + 1) + right] -
                    integral[top * (width + 1) + right] -
                    integral[bottom * (width + 1) + left] +
                    integral[top * (width + 1) + left]
                val threshold = sum / area - LOCAL_THRESHOLD_OFFSET
                val value = if (luma(pixels[y * width + x]) < threshold) 0 else 255
                pixels[y * width + x] = Color.rgb(value, value, value)
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun luma(color: Int): Int =
        ((color shr 16 and 0xff) * 299 + (color shr 8 and 0xff) * 587 +
            (color and 0xff) * 114) / 1000

    private companion object {
        const val MAX_WIDTH = 1_800f
        const val LOCAL_THRESHOLD_OFFSET = 7
    }
}
