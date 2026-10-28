package io.asv.collectorvision

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Isolated library test context: never reads/writes the host app or collection. */
class OwnTrainingEvidenceStoreTest {
    private fun isolated():Context {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(base.cacheDir,"own-store-test-"+UUID.randomUUID()).apply { check(mkdirs()) }
        return object:ContextWrapper(base) {
            override fun getApplicationContext():Context = this
            override fun getFilesDir():File = folder
        }
    }
    private fun entries(bytes:ByteArray):Map<String,ByteArray> {
        val result=linkedMapOf<String,ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while(true) { val entry=zip.nextEntry ?: break; result[entry.name]=zip.readBytes();zip.closeEntry() }
        }
        return result
    }
    @Test fun optInReviewExportAndOwnership() {
        val context=isolated();val frame=Bitmap.createBitmap(16,24,Bitmap.Config.ARGB_8888)
        val crop=Bitmap.createBitmap(8,12,Bitmap.Config.ARGB_8888)
        OwnTrainingEvidenceStore(context).use { store ->
            assertFalse(store.offer(frame,crop,JSONObject()))
            store.enabled=true
            assertTrue(store.offer(frame,crop,JSONObject().put("engine","unit-test")))
            val output=ByteArrayOutputStream()
            assertEquals(1,store.export(output).get(20,TimeUnit.SECONDS).toInt())
            val files=entries(output.toByteArray());assertEquals(4,files.size)
            val metadata=JSONObject(String(files.entries.single { it.key.endsWith("metadata.json") }.value,Charsets.UTF_8))
            assertFalse(metadata.getBoolean("trainingEligible"));assertEquals("unreviewed",metadata.getString("labelStatus"))
            assertFalse(frame.isRecycled);assertFalse(crop.isRecycled)
            val oracle=UUID.randomUUID().toString()
            assertTrue(store.review(store.sessionId,metadata.getString("captureId"),oracle,null,"Confirmed manually").get(20,TimeUnit.SECONDS))
            val second=ByteArrayOutputStream();store.export(second).get(20,TimeUnit.SECONDS)
            val review=JSONObject(String(entries(second.toByteArray()).entries.single { it.key.endsWith("review.json") }.value,Charsets.UTF_8))
            assertEquals(oracle,review.getString("oracleId"));assertFalse(review.getBoolean("trainingEligible"))
            try { store.review("../escape",metadata.getString("captureId"),null,null,"").get(20,TimeUnit.SECONDS);fail("Unsafe session accepted") }
            catch(expected:java.util.concurrent.ExecutionException) { assertTrue(expected.cause is IllegalArgumentException) }
        }
        frame.recycle();crop.recycle()
    }
    @Test fun quotaRejectsWithoutDeletingAndIgnoresIncompleteSamples() {
        val context=isolated();val frame=Bitmap.createBitmap(16,24,Bitmap.Config.ARGB_8888)
        val root=File(context.filesDir,"own-training-evidence-v1")
        val incomplete=File(root,"partial/capture").apply { check(mkdirs()) }
        File(incomplete,"frame.png").writeBytes(byteArrayOf(1,2,3))
        OwnTrainingEvidenceStore(context,1).use { store ->
            store.enabled=true;assertTrue(store.offer(frame,null,JSONObject()))
            val output=ByteArrayOutputStream();assertEquals(0,store.export(output).get(20,TimeUnit.SECONDS).toInt())
            assertEquals("quota_or_disk_limit",store.lastOutcome)
            assertTrue(File(incomplete,"frame.png").exists());assertTrue(entries(output.toByteArray()).isEmpty())
        }
        frame.recycle()
    }
}
