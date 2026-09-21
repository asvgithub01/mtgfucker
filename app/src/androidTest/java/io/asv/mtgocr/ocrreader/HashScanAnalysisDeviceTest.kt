package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises all four checkbox combinations without reading/writing any collection rows. */
@RunWith(AndroidJUnit4::class)
class HashScanAnalysisDeviceTest {
    @Test fun allCombinationsFinishAndReleaseTheirBitmap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(OpenCVLoader.initLocal())
        val analysis = HashScanAnalysis(context)
        try {
            val prepared = CountDownLatch(1)
            var ready = false
            analysis.prepare(true) { ready = it; prepared.countDown() }
            assertTrue("Preparation timed out", prepared.await(120, TimeUnit.SECONDS))
            assertTrue("Local name index required on this device", ready)
            for (ocr in listOf(false, true)) for (symbol in listOf(false, true)) {
                val bitmap = Bitmap.createBitmap(630, 880, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).apply {
                    drawColor(Color.WHITE)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 38f }
                    drawText("Giant Growth", 40f, 65f, paint)
                    paint.textSize = 25f
                    drawText("© 2014 Wizards of the Coast", 15f, 850f, paint)
                }
                val done = CountDownLatch(1)
                var result: HashScanAnalysis.Result? = null
                analysis.analyze(bitmap, HashScanAnalysis.Options(ocr, symbol)) { result = it; done.countDown() }
                assertTrue("Analysis timed out ocr=$ocr symbol=$symbol", done.await(120, TimeUnit.SECONDS))
                assertTrue(bitmap.isRecycled)
                val actual = checkNotNull(result)
                assertNotNull(actual.hash)
                assertTrue(actual.rows.isNotEmpty())
                assertTrue("${actual.errors}", actual.errors.isEmpty())
                if (ocr) {
                    assertTrue(actual.rawTitle.isNotEmpty())
                    assertEquals(2014, actual.printing?.printingYear)
                    assertTrue(actual.names.any { it.equals("Giant Growth", true) })
                } else {
                    assertTrue(actual.rawTitle.isEmpty())
                    assertNull(actual.printing)
                }
                if (!symbol) assertNull(actual.symbols) else assertNotNull(actual.symbols)
                Log.i("HashChecksTest", "ocr=$ocr symbol=$symbol hash=${actual.hash?.elapsedMs} ocr_ms=${actual.ocrMs} symbol_ms=${actual.symbolMs} total=${actual.elapsedMs}")
            }
        } finally { analysis.close() }
    }
}
