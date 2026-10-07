package io.asv.mtgocr.ocrreader

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import io.asv.collectorvision.EdScanPerf

/** All models read the SAME immutable camera snapshot, never the rendered AR image.
 * Sequential native tasks bound CPU pressure; Activity allows only one snapshot in flight.
 * Ownership remains with caller until the returned task completes (including partial failures).
 */
internal class TripleSnapshotOcr {
    var diagnostics:(String)->Unit = {}
    private val models=listOf(
        "latin" to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
        "japanese" to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
        "korean" to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
        "chinese" to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()))
    fun processLatin(image:InputImage):Task<Text> {
        val started=System.nanoTime()
        return models.first().second.process(image).addOnCompleteListener {
            diagnostics("triple.methodSample method=ocr model=latinFast ms=${(System.nanoTime()-started)/1000000} success=${it.isSuccessful}")
        }
    }
    fun process(image:InputImage,language:String?=null):Task<Text> {
        val readings=mutableListOf<Text>()
        var chain:Task<Void> = Tasks.forResult<Void>(null)
        val selected=TripleLanguageScope.model(language)
        for((name,model) in models.filter { selected==null || it.first==selected }) chain=chain.continueWithTask {
            val started=System.nanoTime()
            model.process(image).continueWith { result ->
                EdScanPerf.record("triple.snapshot.$name",System.nanoTime()-started)
                diagnostics("triple.methodSample method=ocr model=$name ms=${(System.nanoTime()-started)/1000000} success=${result.isSuccessful}")
                if(result.isSuccessful) readings.add(result.result)
                null
            }
        }
        return chain.continueWith {
            readings.maxByOrNull { TripleLanguageEvidence.scriptScore(it.text) }
                ?: throw IllegalStateException("No OCR model completed")
        }
    }
    fun close() { models.forEach { it.second.close() } }
}
