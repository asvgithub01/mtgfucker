package io.asv.mtgocr.ocrreader

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.LinkedHashSet

/** Reads title candidates directly from CameraX frames before OpenCV starts looking for edges. */
class LiveCardNameOcr {
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val japaneseRecognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    @OptIn(markerClass = [ExperimentalGetImage::class])
    fun recognize(imageProxy: ImageProxy, callback: (List<String>, Throwable?) -> Unit) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            callback(emptyList(), IllegalStateException("El frame de cámara no está disponible"))
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        val tasks = listOf(
            latinRecognizer.process(input),
            japaneseRecognizer.process(input)
        )
        Tasks.whenAllComplete(tasks).addOnCompleteListener {
            val width = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
            val height = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
            val titleRegion = OcrTitleRegion.forFrame(width, height)
            val candidates = LinkedHashSet<String>()
            var firstError: Throwable? = null
            tasks.forEach taskLoop@ { task ->
                if (!task.isSuccessful) {
                    if (firstError == null) firstError = task.exception
                    return@taskLoop
                }
                task.result?.textBlocks
                    ?.flatMap { it.lines }
                    ?.sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                    ?.forEach lineLoop@ { line ->
                        val box = line.boundingBox ?: return@lineLoop
                        val text = line.text.replace("|", "").trim()
                        if (titleRegion.containsCenter(box.left, box.top, box.right, box.bottom) &&
                            text.length in 2..80
                        ) {
                            candidates += text
                        }
                    }
            }
            callback(candidates.toList(), firstError.takeIf { candidates.isEmpty() })
        }
    }

    fun close() {
        latinRecognizer.close()
        japaneseRecognizer.close()
    }
}
