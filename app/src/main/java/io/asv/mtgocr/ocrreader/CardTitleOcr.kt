package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.util.LinkedHashSet
import kotlin.math.max
import kotlin.math.min

data class CardTitleOcrResult(
    val lines: List<String>,
    val successfulVariants: Int,
    val attemptedVariants: Int
)

/** Reads the printed card name from two high-resolution header crops. */
class CardTitleOcr {
    private val recognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    fun recognize(card: Bitmap, callback: (CardTitleOcrResult?, Throwable?) -> Unit) {
        val titleCrop = crop(card, .025f, .015f, .90f, .145f)
        val headerCrop = crop(card, .015f, .01f, .985f, .22f)
        val title = enlarge(titleCrop)
        val header = enlarge(headerCrop)
        titleCrop.recycle()
        headerCrop.recycle()
        val variants = listOf(title, autoContrast(title), header, autoContrast(header))
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
                callback(CardTitleOcrResult(lines.toList(), successful, variants.size), null)
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

    private fun luma(color: Int): Int =
        ((color shr 16 and 0xff) * 299 + (color shr 8 and 0xff) * 587 +
            (color and 0xff) * 114) / 1000

    private companion object {
        const val MAX_WIDTH = 1_800f
    }
}
