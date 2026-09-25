package io.asv.mtgocr.ocrreader

import android.graphics.BitmapFactory
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.CheckBox
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HashProbableEditionDeviceTest {
    @Test fun flagDefaultsOffAndDoesNotSelectAnyOtherControl() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().targetContext, R.style.Theme_Mtg)
            val view = LayoutInflater.from(context).inflate(R.layout.activity_rapid_edition_scan, null)
            assertFalse(view.findViewById<CheckBox>(R.id.hashScanProbableEdition).isChecked)
        }
    }

    @Test fun realMyrCanEstimateWithPopupEnabledWhileFlagOffKeepsExistingResolution() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "two_stage_replay/myr.jpg")
        assumeTrue("Private Myr fixture missing", file.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val analysis = HashScanAnalysis(context)
        try {
            val ready = CountDownLatch(1)
            analysis.prepare(true) { assertTrue(it); ready.countDown() }
            assertTrue(ready.await(60, TimeUnit.SECONDS))
            for (enabled in listOf(false, true)) {
                val frame = checkNotNull(BitmapFactory.decodeFile(file.path))
                val done = CountDownLatch(1)
                var result: HashScanAnalysis.Result? = null
                analysis.analyze(frame, HashScanAnalysis.Options(ocr = true, symbol = false,
                    editionPicker = true, probableEdition = enabled)) { result = it; done.countDown() }
                assertTrue(done.await(45, TimeUnit.SECONDS))
                val actual = checkNotNull(result)
                assertTrue(actual.errors.toString(), actual.errors.isEmpty())
                if (!enabled) assertTrue(actual.rows.all { it.probableVariant == null })
                else {
                    val row = actual.rows.single { it.probableVariant != null }
                    assertEquals("Sarcomite Myr", row.candidate.hit.name)
                    assertEquals("FUT", row.probableVariant?.set?.code)
                    assertNotNull(actual.capturedJpeg)
                }
                assertTrue(frame.isRecycled)
            }
        } finally { analysis.close() }
    }
}
