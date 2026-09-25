package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HashSymbolV2PipelineDeviceTest {
    @Test fun rejectedAttemptRecyclesFrameAndPersistsPhotoWithoutAutoAdd() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(OpenCVLoader.initLocal())
        val analysis = HashScanAnalysis(context)
        val frame = Bitmap.createBitmap(630, 880, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val directory = File(context.filesDir, "symbol_scan_v2")
        val before = directory.listFiles().orEmpty().map { it.name }.toSet()
        try {
            val ready = CountDownLatch(1)
            analysis.prepare(false) { ready.countDown() }
            assertTrue(ready.await(30, TimeUnit.SECONDS))
            val finished = CountDownLatch(1)
            var result: HashScanAnalysis.Result? = null
            // V2 wins over V1; evidence is retained even with auto-add/captureEvidence disabled.
            analysis.analyze(frame, HashScanAnalysis.Options(false, true, symbolV2 = true)) {
                result = it
                finished.countDown()
            }
            assertTrue(finished.await(30, TimeUnit.SECONDS))
            val actual = checkNotNull(result)
            assertTrue(frame.isRecycled)
            assertTrue(actual.errors.toString(), actual.errors.isEmpty())
            assertNull(actual.symbols)
            assertNotNull(actual.symbolV2)
            assertNull(actual.symbolV2?.selectedSet)
            assertTrue(actual.rows.all { it.resolvedVariant == null && it.compatibleVariants.isEmpty() })
            assertNotNull(actual.capturedJpeg)
            val attempt = directory.listFiles().orEmpty().single { it.name !in before }
            assertTrue(File(attempt, "card.jpg").length() > 0)
            val metadata = JSONObject(File(attempt, "metadata.json").readText())
            assertTrue(metadata.getJSONObject("checks").getBoolean("symbolV2"))
            assertEquals("sin_recorte_de_simbolo", metadata.getJSONObject("symbolV2").getString("reason"))
        } finally { analysis.close() }
    }
}
