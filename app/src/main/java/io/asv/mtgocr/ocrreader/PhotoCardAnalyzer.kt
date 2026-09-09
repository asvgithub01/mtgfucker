package io.asv.mtgocr.ocrreader

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.io.File

/** Runs the bundled Japanese/Latin recognizer once over a persisted still photo. */
object PhotoCardAnalyzer {
    fun recognize(
        context: Context,
        imagePath: String,
        callback: (List<String>, Throwable?) -> Unit
    ) {
        val recognizer = TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )
        val image = runCatching {
            InputImage.fromFilePath(context, Uri.fromFile(File(imagePath)))
        }.getOrElse {
            recognizer.close()
            callback(emptyList(), it)
            return
        }
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val lines = text.textBlocks
                    .flatMap { it.lines }
                    .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                    .map { it.text.trim() }
                    .filter { value ->
                        value.length in 2..60 &&
                            value.any(Char::isLetter) &&
                            value.count(Char::isDigit) * 2 < value.length
                    }
                callback(lines, null)
            }
            .addOnFailureListener { callback(emptyList(), it) }
            .addOnCompleteListener { recognizer.close() }
    }
}
