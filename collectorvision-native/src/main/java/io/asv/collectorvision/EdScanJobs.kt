/* SPDX-License-Identifier: AGPL-3.0-or-later */
package io.asv.collectorvision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.nl.languageid.LanguageIdentification

/** Files are the durable inputs/results; WorkManager stores independent, idempotent work requests. */
object EdScanJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun directory(context: Context) = File(context.filesDir, "edscan").apply { mkdirs() }
    internal fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun price(context: Context, id: String): String {
        require(id.matches(Regex("[a-fA-F0-9-]{36}")))
        val name = "edscan-price-$id"
        val request = OneTimeWorkRequestBuilder<EdScanPriceWorker>()
            .setInputData(workDataOf("id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
        return name
    }
    fun capture(context: Context, bitmap: Bitmap, corners: List<Corner>, id: String, finish: String): String {
        val key = UUID.randomUUID().toString()
        val app = context.applicationContext
        scope.launch {
            try {
                val dir = directory(app)
                val rectified = Bitmap.createBitmap(744, 1040, Bitmap.Config.ARGB_8888)
                try {
                    val source = corners.flatMap { listOf(it.x * bitmap.width, it.y * bitmap.height) }.toFloatArray()
                    val transform = android.graphics.Matrix()
                    check(source.size == 8 && transform.setPolyToPoly(source, 0,
                        floatArrayOf(0f, 0f, 744f, 0f, 744f, 1040f, 0f, 1040f), 0, 4))
                    android.graphics.Canvas(rectified).drawBitmap(bitmap, transform, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    val bytes = java.io.ByteArrayOutputStream().use { out ->
                        check(rectified.compress(Bitmap.CompressFormat.JPEG, 95, out)); out.toByteArray()
                    }
                    write(File(dir, "$key.jpg"), bytes)
                } finally { rectified.recycle() }
                write(File(dir, "$key.json"), JSONObject().put("id", id).put("finish", finish)
                    .put("createdAt", System.currentTimeMillis()).put("languageStatus", "pending")
                    .toString().toByteArray())
                language(app, key)
            } catch (e: Exception) { android.util.Log.e("EdScanJobs", "Photo not saved", e) }
            finally { bitmap.recycle() }
        }
        return key
    }
    fun link(context: Context, key: String, library: String, collectionItemId: String, printingUuid: String) {
        val app = context.applicationContext
        scope.launch {
            try {
                write(File(directory(app), "$key.link.json"), JSONObject().put("library", library)
                    .put("collectionItemId", collectionItemId).put("printingUuid", printingUuid).toString().toByteArray())
            } catch (e: Exception) { android.util.Log.e("EdScanJobs", "Photo association not saved", e) }
        }
    }
    private fun language(context: Context, key: String) {
        val request = OneTimeWorkRequestBuilder<EdScanLanguageWorker>()
            .setInputData(workDataOf("key" to key)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("edscan-ocr-$key", ExistingWorkPolicy.KEEP, request)
    }
    fun resume(context: Context) {
        val app = context.applicationContext
        scope.launch {
            directory(app).listFiles()?.filter { it.extension == "jpg" }?.forEach { photo ->
                val result = File(photo.parentFile, "${photo.nameWithoutExtension}.ocr.json")
                if (!result.exists()) language(app, photo.nameWithoutExtension)
            }
        }
    }
}

class EdScanPriceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        if (!id.matches(Regex("[a-fA-F0-9-]{36}"))) return@withContext Result.failure()
        var connection: HttpURLConnection? = null
        try {
            val file = File(EdScanJobs.directory(applicationContext), "price-$id.json")
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < 86400000) return@withContext Result.success()
            connection = URL("https://api.scryfall.com/cards/$id").openConnection() as HttpURLConnection
            connection.connectTimeout = 5000; connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "EdScan/0.3")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code == 404 || code == 400) return@withContext Result.failure()
            if (code != 200) return@withContext Result.retry()
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            check(json.getString("id").equals(id, true))
            EdScanJobs.write(file, json.toString().toByteArray())
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
        finally { connection?.disconnect() }
    }
}

class EdScanLanguageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object { private val serial = Mutex() }
    override suspend fun doWork(): Result = serial.withLock { withContext(Dispatchers.IO) {
        val key = inputData.getString("key") ?: return@withContext Result.failure()
        if (!key.matches(Regex("[a-fA-F0-9-]{36}"))) return@withContext Result.failure()
        val dir = EdScanJobs.directory(applicationContext)
        val resultFile = File(dir, "$key.ocr.json")
        if (resultFile.exists()) return@withContext Result.success()
        val bitmap = BitmapFactory.decodeFile(File(dir, "$key.jpg").path) ?: return@withContext Result.failure()
        val readers = listOf(
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()))
        val language = LanguageIdentification.getClient()
        try {
            val result = JSONObject().put("status", "review_required")
                .put("unsupportedOcr", "ru").put("autoApplied", false)
            val readings = org.json.JSONArray()
            // Sequential on purpose: four OCR models must not compete with camera inference in parallel.
            for ((index, reader) in readers.withIndex()) {
                ensureActive()
                val text = reader.process(InputImage.fromBitmap(bitmap, 0)).await().text
                val candidates = language.identifyPossibleLanguages(text).await()
                val guesses = org.json.JSONArray()
                candidates.filter { it.languageTag != "und" }.forEach {
                    guesses.put(JSONObject().put("language", it.languageTag).put("confidence", it.confidence))
                }
                readings.put(JSONObject().put("script", listOf("latin", "chinese", "japanese", "korean")[index])
                    .put("text", text).put("candidates", guesses))
            }
            result.put("readings", readings)
            EdScanJobs.write(resultFile, result.toString().toByteArray())
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
        finally { readers.forEach { it.close() }; language.close(); bitmap.recycle() }
    }
}
}
