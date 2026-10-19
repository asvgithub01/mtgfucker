package io.asv.mtgocr.ocrreader

import android.content.Context
import androidx.work.*
import io.asv.collectorvision.EdScanJobs
import io.asv.collectorvision.EdScanLanguageWorker
import io.asv.collectorvision.EdScanVerificationWorker
import io.asv.mtgocr.ocrreader.data.CardRepository
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/** One durable chain per photo: OCR + title cross-check; visual verification only for OCR-origin scans. Inventory writes require the explicit reliable-save stage. */
object ReviewAnalysisBatch {
    data class Started(val tag: String?, val unchanged: Int, val reused: Int, val fresh: Int)
    /** Same skip policy as the worker, computed off the UI thread. No re-offer of unchanged manual cases. */
    fun actionable(context: Context, library: String, entries: List<ScanReviewStore.Entry>): Set<String> {
        val dir=EdScanJobs.directory(context)
        val journals=File(context.filesDir,"review_batches").listFiles().orEmpty()
            .filter { ScanReviewStore.json(File(it,"batch.json"))?.optString("library")==library }
            .sortedByDescending { ScanReviewStore.json(File(it,"batch.json"))?.optLong("createdAt") ?: 0L }
        return entries.filter { it.state!="reviewed" && ScanReviewStore.hasPhoto(context,it.photoKey) }
            .distinctBy { it.photoKey }.filter { entry ->
                val attempt=ScanReviewStore.json(File(dir,"${entry.photoKey}.visual-request.json"))?.optString("attempt").orEmpty()
                !attempt.matches(Regex("[a-f0-9-]{36}")) || !ReviewEvidenceCache.unchanged(dir,entry,attempt,journals)
            }.map { it.photoKey }.toSet()
    }
    fun start(context: Context, library: String, entries: List<ScanReviewStore.Entry>, reliableSave: Boolean = false): Started {
        val dir = EdScanJobs.directory(context)
        val tag = "review-batch-${UUID.randomUUID()}"
        val work = WorkManager.getInstance(context)
        val photos = entries.distinctBy { it.photoKey }.filter {
            it.photoKey.matches(Regex("[0-9a-f-]{36}")) && File(dir,"${it.photoKey}.jpg").isFile && File(dir,"${it.photoKey}.json").isFile
        }
        val journals=File(context.filesDir,"review_batches").listFiles().orEmpty()
            .filter { ScanReviewStore.json(File(it,"batch.json"))?.optString("library")==library }
            .sortedByDescending { ScanReviewStore.json(File(it,"batch.json"))?.optLong("createdAt") ?: 0L }
        val reusable=if(reliableSave) photos.mapNotNull { entry ->
            ReviewEvidenceCache.attempt(dir,entry.photoKey,entry.source!="ocr")?.let { entry.photoKey to it }
        }.toMap() else emptyMap()
        val pending=photos.filterNot { entry -> reusable[entry.photoKey]?.let {
            ReviewEvidenceCache.unchanged(dir,entry,it,journals)
        } == true }
        val unchanged=photos.size-pending.size
        if(pending.isEmpty()) return Started(null,unchanged,0,0)
        if(reliableSave) ReliableReviewJournal.prepare(context,library,tag,pending)
        if(reliableSave) context.getSharedPreferences("review_batch",0).edit()
            .putInt("$tag-total",photos.size).putInt("$tag-unchanged",unchanged).putInt("$tag-planned",pending.size).apply()
        for (entry in pending) {
            val key = entry.photoKey
            val cached=reusable[key]
            val attempt = cached ?: UUID.randomUUID().toString()
            // Original EdScan OCR can be reused even before a full review has ever run.
            val originalOcr=File(dir,"$key.ocr.json")
            val plan=ReviewAnalysisPolicy.plan(entry.source,reliableSave,cached!=null,originalOcr.isFile &&
                ScanReviewStore.json(originalOcr)!=null && listOf(File(dir,"$key.jpg"),File(dir,"$key.json")).all { it.lastModified()<=originalOcr.lastModified() })
            val languageOnly=plan.languageOnly
            val copyOriginal=plan.copyOriginalOcr
            if(copyOriginal) originalOcr.copyTo(File(dir,"$key.$attempt.ocr.json"),overwrite=true)
            ScanReviewStore.write(File(dir,"$key.visual-request.json"),JSONObject().put("attempt",attempt).put("both",true).put("languageOnly",languageOnly))
            val data = workDataOf("key" to key,"attempt" to attempt,"library" to library,"batch" to tag)
            val visual = OneTimeWorkRequestBuilder<EdScanVerificationWorker>().setInputData(data).addTag(tag).build()
            val ocr = OneTimeWorkRequestBuilder<EdScanLanguageWorker>().setInputData(data).addTag(tag).build()
            val compare = OneTimeWorkRequestBuilder<ReviewTitleWorker>().setInputData(data).addTag(tag)
                .apply { if(cached!=null || copyOriginal) addTag("review-reused-evidence") }.build()
            val chain=when {
                cached!=null || copyOriginal -> work.beginUniqueWork("review-full-$key",ExistingWorkPolicy.REPLACE,compare)
                languageOnly -> work.beginUniqueWork("review-full-$key",ExistingWorkPolicy.REPLACE,ocr).then(compare)
                else -> work.beginUniqueWork("review-full-$key", ExistingWorkPolicy.REPLACE, visual).then(ocr).then(compare)
            }
            if(reliableSave) chain.then(OneTimeWorkRequestBuilder<ReliableReviewWorker>().setInputData(data)
                .addTag(tag).addTag(ReliableReviewWorker.TAG).build()).enqueue()
            else chain.enqueue()
        }
        context.getSharedPreferences("review_batch",0).edit().putString(library,tag).apply()
        val reused=pending.count { it.photoKey in reusable }
        return Started(tag,unchanged,reused,pending.size-reused)
    }
}

/** A title mismatch is evidence for a human, never permission to rewrite the collection. */
class ReviewTitleWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val key=inputData.getString("key").orEmpty()
        val attempt=inputData.getString("attempt").orEmpty()
        val library=inputData.getString("library").orEmpty()
        if (!key.matches(Regex("[0-9a-f-]{36}")) || !attempt.matches(Regex("[0-9a-f-]{36}"))) return@withContext Result.failure()
        val dir=EdScanJobs.directory(applicationContext)
        try {
            val link=ScanReviewStore.json(File(dir,"$key.link.json")) ?: return@withContext Result.failure()
            if(link.optString("library")!=library) return@withContext Result.failure()
            // Read the captured library explicitly: changing the active library must not redirect a job.
            val collection=DataUtils.readSerializable<io.asv.mtgocr.ocrreader.model.Biblio>(applicationContext,library)
            val card=collection?.cards?.firstOrNull { it.collectionItemId==link.optString("collectionItemId") }
                ?: return@withContext Result.failure()
            val ocr=ScanReviewStore.json(File(dir,"$key.$attempt.ocr.json")) ?: return@withContext Result.failure()
            val titles=buildList<String> {
                val readings=ocr.optJSONArray("readings") ?: return@buildList
                for(i in 0 until readings.length()) {
                    val lines=readings.optJSONObject(i)?.optJSONArray("titleLines") ?: continue
                    for(j in 0 until lines.length()) add(lines.optString(j))
                }
            }
            val repo=CardRepository.get(applicationContext)
            val matches=withTimeout(90000) { suspendCancellableCoroutine<List<io.asv.mtgocr.ocrreader.data.PhotoCardNameMatch>> { c ->
                repo.matchLocalPhotoText(titles,true) { if(c.isActive) c.resume(it) }
            } }
            val options=withTimeout(90000) { suspendCancellableCoroutine<List<io.asv.mtgocr.ocrreader.data.CardEditionOption>> { c ->
                repo.loadCachedPrinting(card.name,card.printingUuid,null,card.setCode.orEmpty(),card.collectorNumber.orEmpty()) { options,error ->
                    if(c.isActive) { if(error!=null) c.resume(emptyList()) else c.resume(options.filter { it.printingUuid==card.printingUuid }) }
                }
            } }
            val expected=options.firstOrNull()?.cardName.orEmpty()
            val observed=matches.map { it.canonicalName }.distinct()
            val state=ReviewTitlePolicy.compare(expected,observed)
            ScanReviewStore.write(File(dir,"$key.$attempt.title.json"),JSONObject()
                .put("state",state).put("expected",expected).put("observed",observed.joinToString(" / "))
                .put("printingUuid",card.printingUuid.orEmpty()).put("autoApplied",false))
            Result.success()
        } catch(_: TimeoutCancellationException) { if(runAttemptCount<2) Result.retry() else Result.failure() }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { if(runAttemptCount<2) Result.retry() else Result.failure() }
    }
}

object ReviewTitlePolicy {
    fun compare(expected: String, observed: List<String>): String {
        if(expected.isBlank() || observed.size!=1) return "inconclusive"
        return if(expected.equals(observed.single(),true)) "consistent" else "conflict"
    }
}
