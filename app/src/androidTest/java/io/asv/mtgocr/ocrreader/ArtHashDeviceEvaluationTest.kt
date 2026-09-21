package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

/** Read-only measurement of the retained debug photos on the connected test device. */
@RunWith(AndroidJUnit4::class)
class ArtHashDeviceEvaluationTest {
    @Test fun compareRetainedPhotos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(BuildConfig.DEBUG && BuildConfig.GIT_BRANCH in setOf(
            "codex/art-hash-identification",
            "feature/improbe-hash-scanner"
        ))
        val samples = File(context.filesDir, "art_hash_samples")
            .listFiles { file -> file.isFile && file.extension == "jpg" }
            ?.sortedBy { it.name }.orEmpty()
        assumeTrue("No hay capturas retenidas en este dispositivo", samples.isNotEmpty())
        assertTrue(OpenCVLoader.initLocal())
        val loadStarted = SystemClock.elapsedRealtime()
        val index = ArtHashIndex.read(context.assets.open(ArtHashIndex.ASSET))
        val matcher = ArtHashMatcher(index)
        val loadMs = SystemClock.elapsedRealtime() - loadStarted
        Log.i(TAG, "index_loaded_ms=$loadMs samples=${samples.size}")
        assertTrue(index.size > 50_000)
        val detector = OpenCvCardDetector(guideAssisted = true)
        for (file in samples) {
            val photo = decodeLikeScanner(file) ?: continue
            val detection = detector.detect(photo)
            if (detection == null) {
                Log.i(TAG, "${file.name.take(13)} no_card")
                photo.recycle()
                continue
            }
            val corners = detection.cornersFor(photo.width, photo.height)
            Log.i(TAG, "${file.name.take(13)} corners=${corners.joinToString { point ->
                "${"%.3f".format(point.x / photo.width)},${"%.3f".format(point.y / photo.height)}"
            }}")
            val source = floatArrayOf(
                corners[0].x, corners[0].y, corners[1].x, corners[1].y,
                corners[2].x, corners[2].y, corners[3].x, corners[3].y
            )
            val destination = floatArrayOf(0f, 0f, 630f, 0f, 630f, 880f, 0f, 880f)
            val transform = Matrix()
            assertTrue(transform.setPolyToPoly(source, 0, destination, 0, 4))
            val corrected = Bitmap.createBitmap(630, 880, Bitmap.Config.ARGB_8888)
            Canvas(corrected).apply {
                drawColor(Color.BLACK)
                drawBitmap(photo, transform, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val result = matcher.match(corrected)
            Log.i(
                TAG,
                "${file.name.take(13)} border=${"%.2f".format(detection.confidence)} " +
                    "match_ms=${result.elapsedMs} top=${result.candidates.take(3).joinToString { hit ->
                        "${hit.hit.name}:p${hit.hit.phashDistance}/d${hit.hit.dhashDistance}/${hit.crop}"
                    }}"
            )
            corrected.recycle()
            photo.recycle()
        }
    }

    private fun decodeLikeScanner(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1_800 || bounds.outHeight / sample > 1_800) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null
        val orientation = ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private companion object { const val TAG = "ArtHashEval" }
}
