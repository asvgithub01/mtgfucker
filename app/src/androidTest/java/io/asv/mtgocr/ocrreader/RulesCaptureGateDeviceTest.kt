package io.asv.mtgocr.ocrreader
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class RulesCaptureGateDeviceTest {
    @Test fun savedPhotosOnlyFlagKnownMisframedPair() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.noBackupFilesDir, "rules_replay/capture_v17")
        val files = dir.listFiles().orEmpty().filter { it.extension == "jpg" }
        assertTrue("Missing private corpus", files.size >= 40)
        val flagged = mutableSetOf<String>()
        val timings = mutableListOf<Long>()
        for (file in files) {
            val bitmap = checkNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try {
                val start = SystemClock.elapsedRealtime()
                val quality = OcrCaptureQuality.measure(bitmap)
                if (RulesCaptureGate.suspicious(quality)) flagged += file.name.take(13)
                timings += SystemClock.elapsedRealtime() - start
            } finally { bitmap.recycle() }
        }
        assertEquals(setOf("1790329335263", "1790329341125"), flagged)
        File(dir.parentFile, "capture_v17_report.txt").writeText(
            "photos=${files.size} flagged=$flagged medianMeasureMs=${timings.sorted()[timings.size / 2]} maxMeasureMs=${timings.maxOrNull()}")
    }
}
