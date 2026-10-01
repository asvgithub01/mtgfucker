package io.asv.mtgocr.ocrreader

import android.content.Context
import android.util.AtomicFile
import io.asv.collectorvision.EdScanJobs
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.json.JSONObject
import java.io.File

object ScanReviewStore {
    data class Entry(val key: String, val card: CardInfo, val source: String, val state: String,
        val language: String?, val candidateId: String, val candidateLabel: String, val stamp: Long, val candidateImage: String = "", val photoKey: String = key, val visual: Boolean = source == "ocr", val candidateSet: String = "", val provisional: Boolean = false, val titleState: String = "", val observedTitle: String = "", val artScore: Double? = null, val artMargin: Double? = null, val analysedAt: Long = 0)
    fun json(file: File): JSONObject? = runCatching { JSONObject(file.readText()) }.getOrNull()
    fun read(context: Context, library: String): List<Entry> {
        if (LibraryCatalog.activeFile(context) != library) return emptyList()
        val cards = LegacyCollectionStore.cards(context).associateBy { it.collectionItemId }
        val dir = EdScanJobs.directory(context)
        val provisional = dir.listFiles().orEmpty().filter { it.name.endsWith(".provisional.json") }.mapNotNull { file ->
            val marker=json(file) ?: return@mapNotNull null
            if(marker.optString("library") != library) return@mapNotNull null
            val card=cards[marker.optString("collectionItemId")] ?: return@mapNotNull null
            val key=file.name.removeSuffix(".json")
            val review=json(File(dir,"$key.review.json"))
            val reviewed=review != null && review.optLong("stamp")==file.lastModified() &&
                review.optString("printingUuid")==card.printingUuid.orEmpty() && review.optString("finish")==card.finish.orEmpty() &&
                review.optString("language")==card.languageCode
            Entry(key,card.snapshotForPersistence(),"edscan",if(reviewed) "reviewed" else "conflict",null,"","",file.lastModified(),
                card.imgPath.orEmpty(),marker.optString("photoKey").ifBlank { key.substringBefore(".provisional") },true,card.setCode.orEmpty(),true)
        }
        return (provisional + dir.listFiles().orEmpty().filter { it.name.endsWith(".link.json") }.flatMap { link ->
            val identity = json(link) ?: return@flatMap emptyList()
            if (identity.optString("library") != library) return@flatMap emptyList()
            val card = cards[identity.optString("collectionItemId")] ?: return@flatMap emptyList()
            val photoKey = link.name.removeSuffix(".link.json")
            val requestJson = json(File(dir,"$photoKey.visual-request.json"))
            val request = requestJson?.optString("attempt").orEmpty()
            val both = requestJson?.optBoolean("both") == true
            val input = json(File(dir,"$photoKey.json")) ?: return@flatMap emptyList()
            val source = input.optString("source").ifBlank { "edscan" }
            val latest = listOf(request).filter { it.matches(Regex("[0-9a-f-]{36}")) }
            // Keep EdScan language evidence, but do not show an obsolete visual result as a second conflict.
            val attempts = if(requestJson?.optBoolean("languageOnly")==true && latest.isNotEmpty()) listOf("language:"+request)
                else if(both && latest.isNotEmpty()) listOf("language:"+request,request)
                else if(source == "ocr" && latest.isNotEmpty()) latest else listOf("") + latest
            attempts.map { attempt ->
                val languageAttempt = attempt.startsWith("language:")
                val resultKey = if(attempt.isBlank()) photoKey else "$photoKey.${attempt.removePrefix("language:")}"
                val key = if(languageAttempt) "$resultKey.language" else resultKey
                val visual = !languageAttempt && (source == "ocr" || attempt.isNotBlank())
                val resultFile = File(dir, if (visual) "$resultKey.verification.json" else "$resultKey.ocr.json")
                val result = json(resultFile)
                var language: String? = null
                var candidateId = ""
                var label = ""
                var candidateImage = ""
                var candidateSet = ""
                var state = "pending"
                val matchFile = File(dir,"$key.match.json")
                if (result != null && visual) {
                    if (result.optString("observedScryfallId").isNotBlank()) {
                        candidateId = result.optString("observedScryfallId")
                        state = "inconclusive"
                        val match = json(matchFile)
                        if (match != null && match.optString("candidateId",candidateId) == candidateId) {
                            candidateImage = match.optString("imageUrl")
                            candidateSet = match.optString("set")
                            label = match.optString("name")+" · "+match.optString("set")+" #"+match.optString("number")
                            state = ReviewPresentationPolicy.candidateState(result.optString("status")!="inconclusive",card.printingUuid == match.optString("printingUuid"))
                        }
                    } else state = "inconclusive"
                } else if (result != null) {
                    val readings = result.optJSONArray("readings")
                    val guesses = mutableListOf<LanguageReviewPolicy.Guess>()
                    if (readings != null) for (i in 0 until readings.length()) {
                        val reading=readings.optJSONObject(i) ?: continue
                        val candidates=reading.optJSONArray("candidates") ?: continue
                        for (j in 0 until candidates.length()) {
                            val guess=candidates.optJSONObject(j) ?: continue
                            guesses += LanguageReviewPolicy.Guess(guess.optString("language"),guess.optDouble("confidence"),reading.optString("text").length)
                        }
                    }
                    language=LanguageReviewPolicy.propose(guesses)
                    state=when { language==null -> "inconclusive"; card.languageCode.isBlank() -> "suggestion"
                        card.languageCode == language -> "consistent"; else -> "conflict" }
                }
                val titleFile = File(dir,"$resultKey.title.json")
                val title = json(titleFile)?.takeIf { it.optString("printingUuid")==card.printingUuid.orEmpty() }
                val titleState = title?.optString("state").orEmpty()
                val ocr=json(File(dir,"$resultKey.ocr.json"))
                val rawTitle=ocr?.optJSONArray("readings")?.optJSONObject(0)?.optJSONArray("titleLines")?.let { lines -> (0 until lines.length()).joinToString(" ") { lines.optString(it) } }.orEmpty()
                val hits=if(visual) result?.optJSONArray("hits") else null
                val score=hits?.optJSONObject(0)?.optDouble("score")?.takeIf { it.isFinite() }
                val runner=hits?.optJSONObject(1)?.optDouble("score")?.takeIf { it.isFinite() }
                if(titleState=="conflict") state="conflict"
                val stamp = maxOf(resultFile.lastModified(),matchFile.lastModified(),titleFile.lastModified())
                val review=json(File(dir,"$key.review.json"))
                if (review != null && review.optLong("stamp") == stamp &&
                    review.optString("printingUuid") == card.printingUuid.orEmpty() &&
                    review.optString("finish") == card.finish.orEmpty() && review.optString("language") == card.languageCode) state="reviewed"
                Entry(key,card.snapshotForPersistence(),source,state,language,candidateId,label,stamp,candidateImage,photoKey,visual,candidateSet,false,titleState,title?.optString("observed").orEmpty().ifBlank { rawTitle },score,if(score!=null && runner!=null) score-runner else null,result?.optLong("checkedAt") ?: 0L)
            }
        }).sortedWith(compareBy<Entry> { when(it.state) { "conflict"->0; "suggestion"->1; "pending"->2; "inconclusive"->3; else->4 } }.thenByDescending { it.key })
    }
    fun writeProvisional(context: Context, scanKey: String, photoKey: String?, library: String, item: String, candidates: List<String>) {
        require(scanKey.matches(Regex("[a-f0-9-]{36}")))
        write(File(EdScanJobs.directory(context),"$scanKey.provisional.json"), JSONObject()
            .put("library",library).put("collectionItemId",item).put("photoKey",photoKey.orEmpty())
            .put("candidates",org.json.JSONArray(candidates)).put("reason","same_card_uncertain_printing"))
    }
    fun write(file: File, json: JSONObject) {
        val atomic=AtomicFile(file); val stream=atomic.startWrite()
        try { stream.write(json.toString().toByteArray()); atomic.finishWrite(stream) }
        catch(e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun acknowledge(context: Context, entry: Entry, card: CardInfo) {
        write(File(EdScanJobs.directory(context),"${entry.key}.review.json"),JSONObject()
            .put("printingUuid",card.printingUuid.orEmpty()).put("finish",card.finish.orEmpty())
            .put("language",card.languageCode).put("stamp",entry.stamp).put("reviewedAt",System.currentTimeMillis()))
    }
}
