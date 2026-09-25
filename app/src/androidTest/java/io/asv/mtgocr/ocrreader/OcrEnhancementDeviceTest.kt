package io.asv.mtgocr.ocrreader

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OcrEnhancementDeviceTest {
    @Test fun replayHadaFootersWithoutCollectionWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.noBackupFilesDir, "rules_replay")
        var complete = 0
        val report = StringBuilder()
        for (file in listOf("hada_good.jpg", "hada_bad.jpg")) {
            val bitmap = checkNotNull(BitmapFactory.decodeFile(File(dir, file).path))
            val reader = PrintingLineOcr()
            val done = CountDownLatch(1)
            var result: PrintingLineOcrResult? = null
            var failure: Throwable? = null
            val start = android.os.SystemClock.elapsedRealtime()
            try {
                reader.recognize(bitmap, retainSpatialLines = true) { actual, error ->
                    result = actual; failure = error; done.countDown()
                }
                assertTrue(done.await(60, TimeUnit.SECONDS))
                assertNull(failure)
                val actual = checkNotNull(result)
                val parsed = StructuredPrintingEvidence.read(actual.spatialLines, setOf("ORI", "M20"))
                report.appendLine("$file enhancedMs=${android.os.SystemClock.elapsedRealtime()-start} footer=${parsed.footer} passes=${parsed.completeTuplePasses} state=${parsed.state}")
                File(dir, "ocr_quality_replay.txt").writeText(report.toString())
                assertEquals(11, actual.attemptedVariants)
                assertTrue(parsed.candidates.all { it.setCode == "ORI" && (it.number == null || it.number == "57") })
                if (parsed.corroboratedTuple) complete++
                actual.preview.recycle()
                val baselineDone = CountDownLatch(1)
                val baselineStart = android.os.SystemClock.elapsedRealtime()
                reader.recognize(bitmap) { baseline, error ->
                    failure = error; baseline?.preview?.recycle(); baselineDone.countDown()
                }
                assertTrue(baselineDone.await(60, TimeUnit.SECONDS))
                assertNull(failure)
                report.appendLine("$file legacy7PassesMs=${android.os.SystemClock.elapsedRealtime()-baselineStart} (warm, second)")
                File(dir, "ocr_quality_replay.txt").writeText(report.toString())
            } finally { reader.close(); bitmap.recycle() }
        }
        assertTrue("The previously readable ORI capture must remain readable", complete >= 1)
    }
}
