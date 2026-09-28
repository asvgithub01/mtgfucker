package io.asv.mtgocr.ocrreader

import android.content.Context
import androidx.work.*
import io.asv.collectorvision.EdScanJobs
import io.asv.mtgocr.ocrreader.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Persist the already requested exact Scryfall price independently of Activity lifetime. */
class EdScanStoredPriceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val library = inputData.getString("library") ?: return@withContext Result.failure()
        val item = inputData.getString("item") ?: return@withContext Result.failure()
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val uuid = inputData.getString("uuid") ?: return@withContext Result.failure()
        val finish = inputData.getString("finish") ?: return@withContext Result.failure()
        val currency = inputData.getString("currency") ?: return@withContext Result.failure()
        if (!id.matches(Regex("[a-fA-F0-9-]{36}")) || File(library).name != library) return@withContext Result.failure()
        try {
            val file = File(EdScanJobs.directory(applicationContext), "price-$id.json")
            if (!file.isFile) EdScanJobs.price(applicationContext, id)
            var attempts = 0
            while (!file.isFile && attempts++ < 20) delay(500)
            if (!file.isFile) return@withContext Result.retry()
            val json = JSONObject(file.readText())
            check(json.getString("id").equals(id, true))
            val key = currency.lowercase() + when(finish) { "foil" -> "_foil"; "etched" -> "_etched"; else -> "" }
            val amount = json.optJSONObject("prices")?.optString(key)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0 } ?: return@withContext Result.success()
            val now = System.currentTimeMillis()
            CardDatabase.get(applicationContext).cardDao().savePrices(listOf(
                CardPriceEntity(uuid, if (finish == "nonfoil") "normal" else finish, amount, currency, "scryfall", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now)), now)))
            LegacyCollectionStore.applyScanPrice(applicationContext, library, item, uuid, finish, amount, currency)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun enqueue(context: Context, library: String, item: String, id: String, uuid: String, finish: String): String {
            val name = "edscan-stored-price-$item-$uuid-$finish"
            val request = OneTimeWorkRequestBuilder<EdScanStoredPriceWorker>()
                .setInputData(workDataOf("library" to library, "item" to item, "id" to id,
                    "uuid" to uuid, "finish" to finish, "currency" to PriceCurrency.preferred(context)))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
            return name
        }
    }
}
