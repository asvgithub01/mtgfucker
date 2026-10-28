/* SPDX-License-Identifier: AGPL-3.0-or-later */
package io.asv.collectorvision
import android.content.Context
import android.graphics.Bitmap
import androidx.work.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
object FastWebCapture {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun capture(context: Context, bitmap: Bitmap, corners: List<Corner>, id: String, finish: String): String {
        val key = UUID.randomUUID().toString()
        val app = context.applicationContext
        scope.launch {
            try {
                val dir = EdScanJobs.directory(app)
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
                    EdScanJobs.write(File(dir, "$key.jpg"), bytes)
                } finally { rectified.recycle() }
                EdScanJobs.write(File(dir, "$key.json"), JSONObject().put("id", id).put("finish", finish)
                    .put("engine", FastWebDiegoEngine.ID).put("source", "fastweb").put("createdAt", System.currentTimeMillis()).put("languageStatus", "pending")
                    .toString().toByteArray())
                WorkManager.getInstance(app).enqueueUniqueWork("edscan-ocr-$key", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<EdScanLanguageWorker>().setInputData(workDataOf("key" to key)).build())
            } catch (e: Exception) { android.util.Log.e("EdScanJobs", "Photo not saved", e) }
            finally { bitmap.recycle() }
        }
        return key
    }
}
