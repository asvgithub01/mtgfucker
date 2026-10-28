/* SPDX-License-Identifier: AGPL-3.0-or-later */
package io.asv.collectorvision

import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Re-check a saved photo independently of the live fast gate. Never edits the collection. */
class EdScanVerificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = EdScanJobs.reviewMutex.withLock { withContext(Dispatchers.IO) {
        val key = inputData.getString("key") ?: return@withContext Result.failure()
        if (!key.matches(Regex("[0-9a-f-]{36}"))) return@withContext Result.failure()
        val dir = EdScanJobs.directory(applicationContext)
        val attempt = inputData.getString("attempt").orEmpty()
        if(attempt.isNotBlank() && !attempt.matches(Regex("[0-9a-f-]{36}"))) return@withContext Result.failure()
        val resultKey = if(attempt.isBlank()) key else "$key.$attempt"
        val output = File(dir, "$resultKey.verification.json")
        if (output.exists()) return@withContext Result.success()
        val input = File(dir, "$key.json")
        if (!input.exists()) return@withContext Result.retry()
        EdScanInferencePriority.awaitIdle()
        val bitmap = BitmapFactory.decodeFile(File(dir, "$key.jpg").path) ?: return@withContext Result.failure()
        try {
            val metadata = JSONObject(input.readText())
            val expected = metadata.optString("id")
            run {
                EdScanInferencePriority.awaitIdle()
                val fastWeb = metadata.optString("engine") == FastWebDiegoEngine.ID
                val result = if (fastWeb) FastWebDiegoEngine.prepare(applicationContext).use { it.scan(bitmap) }
                    else NativeCollectorVisionEngine.prepare(applicationContext).use { it.scan(bitmap) }
                val best = result.hits.firstOrNull()
                val second = result.hits.drop(1).firstOrNull { it.cardId != best?.cardId }
                val status = if (metadata.optString("source") == "ocr" || attempt.isNotBlank()) {
                    if (VerificationPolicy.status(best?.cardId.orEmpty(), result.present, best?.cardId,
                        best?.score, second?.score) == "consistent") "candidate" else "inconclusive"
                } else VerificationPolicy.status(expected, result.present, best?.cardId, best?.score, second?.score)
                val json = JSONObject().put("status", status).put("expectedScryfallId", expected)
                    .put("observedScryfallId", best?.cardId ?: "").put("checkedAt", System.currentTimeMillis())
                    .put("engine", if (fastWeb) FastWebDiegoEngine.ID else "Cornelius2.12+Milo1").put("autoApplied", false)
                    .put("hits", JSONArray().apply { result.hits.forEach {
                        put(JSONObject().put("id", it.cardId).put("score", if (it.score.isFinite()) it.score.toDouble() else JSONObject.NULL))
                    } })
                EdScanJobs.write(output, json.toString().toByteArray())
            }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if(attempt.isNotBlank() && runAttemptCount >= 2) Result.failure() else Result.retry() }
        finally { bitmap.recycle() }
    } }
}

object VerificationPolicy {
    fun status(expected: String, present: Boolean, observed: String?, score: Float?, runner: Float?): String {
        if (!present || expected.isBlank() || observed.isNullOrBlank() || score == null || runner == null ||
            !score.isFinite() || !runner.isFinite() || score < .70f || score-runner < .08f) return "inconclusive"
        return if (expected.equals(observed, true)) "consistent" else "conflict"
    }
}
