package io.asv.mtgocr.ocrreader

import android.content.Context
import android.util.AtomicFile
import androidx.work.*
import io.asv.collectorvision.EdScanJobs
import io.asv.mtgocr.ocrreader.data.*
import io.asv.mtgocr.ocrreader.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import kotlin.coroutines.resume

/** Durable local journal, independent of the rolling legacy .bak file. */
object ReliableReviewJournal {
    fun directory(context: Context, tag: String): File {
        require(tag.matches(Regex("review-batch-[a-f0-9-]{36}")))
        return File(context.filesDir,"review_batches/$tag").apply { mkdirs() }
    }
    fun bytes(value: Serializable): ByteArray = ByteArrayOutputStream().use { buffer ->
        ObjectOutputStream(buffer).use { it.writeObject(value) }; buffer.toByteArray()
    }
    fun fingerprint(card: CardInfo): String = MessageDigest.getInstance("SHA-256").digest(bytes(card.snapshotForPersistence()))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun snapshot(file: File, value: Serializable) {
        val atomic=AtomicFile(file); val stream=atomic.startWrite()
        try { stream.write(bytes(value)); atomic.finishWrite(stream) }
        catch(e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun prepare(context: Context, library: String, tag: String, photos: List<ScanReviewStore.Entry>) {
        val collection=requireNotNull(DataUtils.readSerializable<Biblio>(context,library))
        val dir=directory(context,tag)
        snapshot(File(dir,"collection-before.ser"),collection.snapshotForPersistence())
        ScanReviewStore.write(File(dir,"batch.json"),JSONObject().put("library",library)
            .put("createdAt",System.currentTimeMillis()).put("photos",photos.size).put("mode","reliable-save"))
        val photoCounts=ScanReviewStore.read(context,library).groupBy { it.card.collectionItemId }.mapValues { (_,rows) -> rows.map { it.photoKey }.distinct().size }
        photos.forEach { entry ->
            val current=requireNotNull(collection.cards.firstOrNull { it.collectionItemId==entry.card.collectionItemId })
            snapshot(File(dir,"${entry.photoKey}.before.ser"),current.snapshotForPersistence())
            ScanReviewStore.write(File(dir,"${entry.photoKey}.plan.json"),JSONObject()
                .put("itemId",current.collectionItemId).put("fingerprint",fingerprint(current)).put("name",current.name)
                .put("multiplePhotos",(photoCounts[current.collectionItemId] ?: 0)>1 || current.quantityCount!=1))
        }
    }
}

/** Save stage: EdScan retains the exact stored printing; OCR-origin scans require visual agreement. */
class ReliableReviewWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    companion object { private val mutex=Mutex(); const val TAG="reliable-apply" }
    override suspend fun doWork(): Result = mutex.withLock { withContext(Dispatchers.IO) {
        val key=inputData.getString("key").orEmpty()
        val attempt=inputData.getString("attempt").orEmpty()
        val library=inputData.getString("library").orEmpty()
        val tag=inputData.getString("batch").orEmpty()
        if(!key.matches(Regex("[a-f0-9-]{36}")) || !attempt.matches(Regex("[a-f0-9-]{36}"))) return@withContext Result.failure()
        val journal=ReliableReviewJournal.directory(applicationContext,tag)
        val outcome=File(journal,"$key.result.json")
        val plan=ScanReviewStore.json(File(journal,"$key.plan.json")) ?: return@withContext Result.failure()
        fun result(status: String, reason: String): Result {
            ScanReviewStore.write(outcome,JSONObject().put("status",status).put("reason",reason)
                .put("name",plan.optString("name")).put("rulesVersion",ReliableReviewPolicy.VERSION).put("at",System.currentTimeMillis()))
            return Result.success(workDataOf("autoStatus" to status,"reason" to reason,"name" to plan.optString("name")))
        }
        // A retried worker must never apply the same correction twice.
        ScanReviewStore.json(outcome)?.takeIf { it.optString("status")=="applied" }?.let {
            return@withContext Result.success(workDataOf("autoStatus" to "applied","reason" to "ok","name" to plan.optString("name")))
        }
        try {
            if(LibraryCatalog.activeFile(applicationContext)!=library) return@withContext result("skipped","changed")
            if(plan.optBoolean("multiplePhotos")) return@withContext result("skipped","multiple")
            val dir=EdScanJobs.directory(applicationContext)
            fun requestCurrent()=ScanReviewStore.json(File(dir,"$key.visual-request.json"))?.optString("attempt")==attempt
            if(!requestCurrent()) return@withContext result("skipped","changed")
            val edscan=!ReliableReviewPolicy.requiresArt(ScanReviewStore.json(File(dir,"$key.json"))?.optString("source").orEmpty())
            val visual=ScanReviewStore.json(File(dir,"$key.$attempt.verification.json"))
            val hits=visual?.optJSONArray("hits")
            val first=hits?.optJSONObject(0)
            val best=first?.optDouble("score")
            val runner=hits?.let { list -> (1 until list.length()).mapNotNull { list.optJSONObject(it) }
                .firstOrNull { it.optString("id")!=first?.optString("id") }?.optDouble("score") }
            val margin=if(best!=null && runner!=null) best-runner else null
            if(!edscan && (visual?.optString("status")!="candidate" || !ReliableReviewPolicy.artReliable(best,margin))) return@withContext result("skipped","weak")
            val title=ScanReviewStore.json(File(dir,"$key.$attempt.title.json")) ?: return@withContext result("skipped","identity")
            val ocr=title.optString("observed")
            val collection=requireNotNull(DataUtils.readSerializable<Biblio>(applicationContext,library))
            val expected=collection.cards.firstOrNull { it.collectionItemId==plan.optString("itemId") }
                ?: return@withContext result("skipped","changed")
            if(ReliableReviewJournal.fingerprint(expected)!=plan.optString("fingerprint") || title.optString("printingUuid")!=expected.printingUuid)
                return@withContext result("skipped","changed")
            val repo=CardRepository.get(applicationContext)
            val options=withTimeout(90000) { suspendCancellableCoroutine<List<CardEditionOption>> { c ->
                if(edscan) repo.loadCachedPrinting(expected.name,expected.printingUuid,null,expected.setCode.orEmpty(),expected.collectorNumber.orEmpty()) { value,error ->
                    if(c.isActive) c.resume(if(error==null) value.filter { it.printingUuid==expected.printingUuid } else emptyList())
                } else repo.loadCollectorVisionPrinting(visual?.optString("observedScryfallId").orEmpty()) { value,error ->
                    if(c.isActive) c.resume(if(error==null) value else emptyList())
                }
            } }
            val option=options.firstOrNull { it.finish==expected.finish } ?: return@withContext result("skipped","catalog")
            val variants=withTimeout(90000) { suspendCancellableCoroutine<List<CardImageVariant>> { c ->
                repo.loadImageLanguages(option.setCode,option.collectorNumber) { value,error ->
                    if(c.isActive) c.resume(if(error==null) value else emptyList())
                }
            } }
            if(variants.isEmpty()) return@withContext result("skipped","catalog")
            val ocrResult=ScanReviewStore.json(File(dir,"$key.$attempt.ocr.json"))
            val guesses=buildList<LanguageReviewPolicy.Guess> {
                val readings=ocrResult?.optJSONArray("readings") ?: return@buildList
                for(i in 0 until readings.length()) {
                    val reading=readings.optJSONObject(i) ?: continue
                    val candidates=reading.optJSONArray("candidates") ?: continue
                    for(j in 0 until candidates.length()) {
                        val guess=candidates.optJSONObject(j) ?: continue
                        add(LanguageReviewPolicy.Guess(guess.optString("language"),guess.optDouble("confidence"),reading.optString("text").length))
                    }
                }
            }
            val titles=buildList<String> {
                val readings=ocrResult?.optJSONArray("readings") ?: return@buildList
                for(i in 0 until readings.length()) {
                    val lines=readings.optJSONObject(i)?.optJSONArray("titleLines") ?: continue
                    for(j in 0 until lines.length()) add(lines.optString(j))
                }
            }
            val matched=ReliableReviewPolicy.titleLanguages(titles,variants.filter { it.languageCode=="en" || !ReliableReviewPolicy.sameIdentity(it.printedName,option.cardName) }.map {
                ReliableReviewPolicy.LocalTitle(it.languageCode,it.printedName)
            })
            val canonicalMatch=title.optString("state")!="inconclusive" && ReliableReviewPolicy.sameIdentity(ocr,option.cardName)
            if(!canonicalMatch && matched.isEmpty()) return@withContext result("skipped","identity")
            val proposed=LanguageReviewPolicy.propose(guesses)
            if(proposed!=null && variants.none { it.languageCode==proposed }) return@withContext result("skipped","edition_language")
            if(proposed!=null && matched.isNotEmpty() && proposed !in matched) return@withContext result("skipped","language")
            val strong=proposed?.takeIf { code -> edscan || guesses.any { it.language==code && it.confidence>=.95 && it.textLength>=30 } }
            val language=(if(edscan) ReliableReviewPolicy.edscanLanguage(canonicalMatch,matched,variants.map { it.languageCode },proposed)
                else ReliableReviewPolicy.resolveLanguage(variants.map { it.languageCode },matched,strong))
                ?: return@withContext result("skipped","language")
            val variant=variants.first { it.languageCode==language }
            val localized=option.copy(imageUrl=variant.imageUrl,displayName=variant.printedName.ifBlank { option.displayName })
            ensureActive()
            // Once a commit begins, finish its legacy/Room/review journal even if the batch is cancelled.
            withContext(NonCancellable) {
                val saved=synchronized(DataUtils::class.java) {
                    if(LibraryCatalog.activeFile(applicationContext)!=library || !requestCurrent()) null else {
                        val latest=DataUtils.readSerializable<Biblio>(applicationContext,library)?.cards?.firstOrNull { it.collectionItemId==expected.collectionItemId }
                        if(latest==null || ReliableReviewJournal.fingerprint(latest)!=plan.optString("fingerprint")) null
                        else LegacyCollectionStore.applyReviewedPrinting(applicationContext,library,expected,localized,language)
                    }
                } ?: return@withContext result("skipped","changed")
                val persisted=DataUtils.readSerializable<Biblio>(applicationContext,library)?.cards?.firstOrNull { it.collectionItemId==saved.collectionItemId }
                check(persisted!=null && ReliableReviewJournal.fingerprint(persisted)==ReliableReviewJournal.fingerprint(saved)) { "Legacy save verification failed" }
                ReliableReviewJournal.snapshot(File(journal,"$key.after.ser"),saved.snapshotForPersistence())
                val roomError=withTimeout(90000) { suspendCancellableCoroutine<Throwable?> { c ->
                    repo.selectReviewedEdition(saved.collectionItemId,localized) { error -> if(c.isActive) c.resume(error) }
                } }
                if(roomError!=null) throw roomError
                val evidence=ScanReviewStore.read(applicationContext,library).filter { it.photoKey==key && it.card.collectionItemId==saved.collectionItemId }
                check(evidence.isNotEmpty())
                evidence.forEach { ScanReviewStore.acknowledge(applicationContext,it,saved) }
                result("applied","ok")
            }
        } catch(e: TimeoutCancellationException) { result("error","catalog") }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { android.util.Log.e("ReliableReview","Failed $key",e); result("error","error") }
    } }
}
