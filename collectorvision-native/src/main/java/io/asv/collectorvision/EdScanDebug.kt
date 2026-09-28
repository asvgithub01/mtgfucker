/* SPDX-License-Identifier: AGPL-3.0-or-later */
package io.asv.collectorvision

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** One immutable acceptance record and separate outcome/host records, linked by a unique scan id. */
object EdScanDebug {
    private val writer = Executors.newSingleThreadExecutor()
    fun accepted(context: Context, evidence: JSONObject): String {
        val key = UUID.randomUUID().toString()
        record(context, key, "accepted", evidence)
        return key
    }
    fun record(context: Context, key: String, kind: String, data: JSONObject) {
        require(key.matches(Regex("[0-9a-f-]{36}")) && kind.matches(Regex("[a-z]+")))
        val app = context.applicationContext
        val bytes = JSONObject(data.toString()).put("scanId", key).put("event", kind)
            .put("recordedAt", System.currentTimeMillis()).toString().toByteArray()
        writer.execute {
            try {
                val dir = File(app.filesDir, "edscan/debug").apply { mkdirs() }
                EdScanJobs.write(File(dir, "$key.$kind.json"), bytes)
            } catch (e: Exception) { android.util.Log.e("EdScanDebug", "Cannot save recognition evidence", e) }
        }
    }
}
