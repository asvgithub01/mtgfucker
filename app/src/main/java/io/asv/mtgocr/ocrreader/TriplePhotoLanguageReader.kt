package io.asv.mtgocr.ocrreader

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Serial, bounded photo-only enrichment. It never owns the live camera's busy/OCR gates. */
internal class TriplePhotoLanguageReader(
    private val index:()->TitleLanguageIndex?,private val log:(String)->Unit
) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor { task ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run() },"triple-photo-language")
    }
    private val pending=AtomicInteger()
    @Volatile private var closed=false
    private var ocr:TripleSnapshotOcr?=null
    private var detector:CardTextLanguageDetector?=null

    data class Report(val decision:CardLanguageResolver.Decision,val readings:List<CardLanguageResolver.Reading>,
        val selectedModel:String,val titleCandidates:Set<String>,val statistics:List<String?>,val elapsedMs:Long=0) {
        fun json()=org.json.JSONObject().put("version",1).put("language",decision.code.orEmpty())
            .put("source",decision.source).put("conflict",decision.conflict).put("model",selectedModel)
            .put("titleCandidates",org.json.JSONArray(titleCandidates.toList())).put("statistics",org.json.JSONArray(statistics))
            .put("elapsedMs",elapsedMs).put("checkedAt",System.currentTimeMillis())
            .put("readings",org.json.JSONArray(readings.map { org.json.JSONObject().put("model",it.model)
                .put("text",it.text).put("titles",org.json.JSONArray(it.titles)) }))
    }
    fun submit(photo:ByteArray,identity:String,callback:(String?)->Unit) =
        submitReport(photo,identity) { callback(it.decision.code) }

    fun submitReport(photo:ByteArray,identity:String,callback:(Report)->Unit) {
        fun failure()=Report(CardLanguageResolver.Decision(null,false,"error"),emptyList(),"",emptySet(),emptyList())
        if(closed || pending.incrementAndGet()>20) {
            if(!closed) pending.decrementAndGet()
            main.post { callback(failure()) };return
        }
        worker.execute {
            val start=SystemClock.elapsedRealtime()
            val result=runCatching {
                if(closed) return@runCatching null
                val bitmap=requireNotNull(BitmapFactory.decodeByteArray(photo,0,photo.size))
                val readings=try {
                    val reader=ocr ?: TripleSnapshotOcr().also { ocr=it }
                    // All successful models read this immutable photo; no extra live camera work.
                    Tasks.await(reader.processAll(InputImage.fromBitmap(bitmap,0))).map { reading ->
                        val lines=reading.text.textBlocks.flatMap { it.lines }
                        CardLanguageResolver.Reading(reading.model,
                            lines.filter { (it.boundingBox?.centerY() ?: 0)<bitmap.height*.94f }
                                .joinToString("\n") { it.text }.take(12000),
                            lines.filter { (it.boundingBox?.centerY() ?: Int.MAX_VALUE)<bitmap.height*.20f }.map { it.text })
                    }
                } finally { bitmap.recycle() }
                if(closed) return@runCatching null
                val selected=CardLanguageResolver.select(readings) ?: return@runCatching null
                val title=index()?.lookup(selected.titles,listOf(identity)) ?: TitleLanguageIndex.Evidence()
                val language=detector ?: CardTextLanguageDetector(useShortRules=false).also { detector=it }
                // Read beyond the old fixed 510..800 box and the first 400 chars. Every chunk
                // keeps the existing confidence/margin gates; conflicting chunks abstain.
                val statistics=selected.text.chunked(400).map { chunk ->
                    val done=CountDownLatch(1)
                    var code:String?=null
                    language.detectText(chunk,"und") {
                        code=TripleScanPolicy.language(it.languageCode,it.confidence);done.countDown()
                    }
                    check(done.await(15,java.util.concurrent.TimeUnit.SECONDS)) { "Language ID timeout" }
                    code
                }
                Report(CardLanguageResolver.resolve(selected.text,CardLanguageResolver.statisticalConsensus(statistics),title),
                    readings,selected.model,title.candidates,statistics)

            }
            pending.decrementAndGet()
            if(!closed) {
                val report=(result.getOrNull() ?: failure()).copy(elapsedMs=SystemClock.elapsedRealtime()-start)
                log("triple.photoLanguage ms=${report.elapsedMs} known=${report.decision.code!=null} code=${report.decision.code ?: "und"} source=${report.decision.source} model=${report.selectedModel} conflict=${report.decision.conflict} ok=${result.isSuccess}")
                main.post { if(!closed) callback(report) }
            }
        }
    }

    fun close() {
        closed=true
        worker.execute { ocr?.close();detector?.close() }
        worker.shutdown()
    }
}
