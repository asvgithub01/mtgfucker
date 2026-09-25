package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ArtHashFrameDeviceTest {
    @Test fun defaultMatchingIncludesNormalModernAndFutureFrames() {
        assertTrue(OpenCVLoader.initLocal())
        // Golden hashes computed independently with scripts/art_hash_poc.py.
        val cases = listOf(
            Triple("normal", Rect(100, 154, 900, 742), 5264436789175907885L to 871784519505749293L),
            Triple("moderno completo", Rect(80, 182, 920, 798), 5552667304893477461L to 871784510848642410L),
            Triple("futurista", Rect(200, 182, 980, 770), 652750771822261805L to 296168192116204589L)
        )
        for ((name, region, hashes) in cases) {
            val photo = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888)
            try {
                photo.eraseColor(Color.LTGRAY)
                val pixels = IntArray(region.width() * region.height()) { i ->
                    val x = i % region.width(); val y = i / region.width()
                    val value = (x / 73 * 37 + y / 91 * 53 + (x / 127) * (y / 113) * 17) % 256
                    Color.rgb(value, value, value)
                }
                photo.setPixels(pixels, 0, region.width(), region.left, region.top, region.width(), region.height())
                val hit = ArtHashMatcher(index(hashes)).match(photo).candidates.first()
                assertEquals(name, hit.crop)
                assertEquals(name, 0, hit.hit.phashDistance)
                assertEquals(name, 0, hit.hit.dhashDistance)
                assertFalse(photo.isRecycled)
            } finally { photo.recycle() }
        }
    }

    /** Optional private fixture: supplied card image, not a live-camera accuracy claim. */
    @Test fun sarcomiteFutureSightSymbolIsFoundNearRightEdge() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "art_frame_replay/sarcomite-myr.png")
        assumeTrue("Private Sarcomite Myr fixture not installed", file.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val references = SetSymbolShapeMatcher(context, OkHttpClient())
        val matcher = SetSymbolHashMatcher(references::referenceMask)
        val photo = checkNotNull(BitmapFactory.decodeFile(file.path))
        try {
            val result = matcher.match(photo, listOf("FUT"))
            android.util.Log.i("ArtHashFrameTest", "Myr symbol=${result.json()}")
            assertEquals(result.json().toString(), "FUT", result.selectedSet)
            assertTrue(result.scores.first().crop[0] > (photo.width * .86).toInt())
        } finally { matcher.close(); references.close(); photo.recycle() }
    }

    /** Optional private fixture: supplied card image, not a live-camera accuracy claim. */
    @Test fun sarcomiteMyrIsFoundInPipelineWithOcrAndSymbolsOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "art_frame_replay/sarcomite-myr.png")
        assumeTrue("Private Sarcomite Myr fixture not installed", file.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val analysis = HashScanAnalysis(context)
        try {
            val prepared = CountDownLatch(1)
            var ready = false
            analysis.prepare(false) { ready = it; prepared.countDown() }
            assertTrue(prepared.await(30, TimeUnit.SECONDS)); assertTrue(ready)
            val frame = checkNotNull(BitmapFactory.decodeFile(file.path))
            val finished = CountDownLatch(1)
            var result: HashScanAnalysis.Result? = null
            analysis.analyze(frame, HashScanAnalysis.Options(ocr = false, symbol = false, symbolV2 = false)) {
                result = it; finished.countDown()
            }
            assertTrue(finished.await(30, TimeUnit.SECONDS))
            val actual = checkNotNull(result)
            assertTrue(actual.errors.toString(), actual.errors.isEmpty())
            assertEquals("Sarcomite Myr", actual.rows.first().candidate.hit.name)
            assertEquals("futurista", actual.rows.first().candidate.crop)
            assertNull(actual.symbols); assertNull(actual.symbolV2)
            assertTrue(actual.names.isEmpty())
            assertTrue(frame.isRecycled)
        } finally { analysis.close() }
    }

    private fun index(hashes: Pair<Long, Long>): ArtHashIndex {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeBytes("AHI1"); out.writeInt(1)
            out.writeLong(hashes.first); out.writeLong(hashes.second)
            out.writeLong(0); out.writeLong(1); out.writeByte(255)
            out.writeLong(0); out.writeLong(2)
            out.writeShort(4); out.writeBytes("test")
            out.writeByte(3); out.writeBytes("fut")
            out.writeByte(1); out.writeBytes("1")
        }
        return ArtHashIndex.read(bytes.toByteArray().inputStream())
    }
}
