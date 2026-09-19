package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.os.SystemClock
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Global artwork candidates from a perspective-corrected, tightly cropped card bitmap. */
internal class ArtHashMatcher(private val index: ArtHashIndex) {
    data class Candidate(val hit: ArtHashIndex.Hit, val crop: String)
    data class Result(val candidates: List<Candidate>, val elapsedMs: Long, val indexedArts: Int)

    private data class Template(
        val name: String, val left: Double, val top: Double,
        val right: Double, val bottom: Double
    )

    fun match(card: Bitmap, limit: Int = 5): Result {
        val started = SystemClock.elapsedRealtime()
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(card, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val bestByArt = LinkedHashMap<String, Candidate>()
            for (template in TEMPLATES) {
                val (phash, dhash) = hashes(gray, template)
                for (hit in index.nearest(phash, dhash, limit)) {
                    val key = hit.illustrationId ?: hit.key
                    val candidate = Candidate(hit, template.name)
                    val old = bestByArt[key]
                    if (old == null || compare(candidate, old) < 0) bestByArt[key] = candidate
                }
            }
            return Result(
                candidates = bestByArt.values.sortedWith(::compare).take(limit),
                elapsedMs = SystemClock.elapsedRealtime() - started,
                indexedArts = index.size
            )
        } finally {
            rgba.release()
            gray.release()
        }
    }

    private fun hashes(gray: Mat, template: Template): Pair<Long, Long> {
        val x1 = (gray.cols() * template.left).toInt().coerceIn(0, gray.cols() - 2)
        val y1 = (gray.rows() * template.top).toInt().coerceIn(0, gray.rows() - 2)
        val x2 = (gray.cols() * template.right).toInt().coerceIn(x1 + 1, gray.cols())
        val y2 = (gray.rows() * template.bottom).toInt().coerceIn(y1 + 1, gray.rows())
        val artwork = gray.submat(Rect(x1, y1, x2 - x1, y2 - y1))
        val small = Mat()
        val floatImage = Mat()
        val coefficients = Mat()
        val strip = Mat()
        try {
            Imgproc.resize(artwork, small, Size(32.0, 32.0), 0.0, 0.0, Imgproc.INTER_AREA)
            small.convertTo(floatImage, CvType.CV_32F)
            Core.dct(floatImage, coefficients)
            val first = FloatArray(64)
            val row = FloatArray(32)
            for (y in 0 until 8) {
                coefficients.get(y, 0, row)
                for (x in 0 until 8) first[y * 8 + x] = row[x]
            }
            val median = first.copyOfRange(1, 64).sorted()[31]
            var phash = 0L
            for (index in 1 until 64) {
                phash = (phash shl 1) or if (first[index] > median) 1L else 0L
            }
            Imgproc.resize(artwork, strip, Size(9.0, 8.0), 0.0, 0.0, Imgproc.INTER_AREA)
            val pixels = ByteArray(9 * 8)
            strip.get(0, 0, pixels)
            var dhash = 0L
            for (y in 0 until 8) for (x in 0 until 8) {
                val left = pixels[y * 9 + x].toInt() and 0xff
                val right = pixels[y * 9 + x + 1].toInt() and 0xff
                dhash = (dhash shl 1) or if (left > right) 1L else 0L
            }
            return phash to dhash
        } finally {
            artwork.release()
            small.release()
            floatImage.release()
            coefficients.release()
            strip.release()
        }
    }

    private fun compare(left: Candidate, right: Candidate): Int {
        val p = left.hit.phashDistance.compareTo(right.hit.phashDistance)
        return if (p != 0) p else left.hit.dhashDistance.compareTo(right.hit.dhashDistance)
    }

    private companion object {
        // Four common frame layouts. The old/white variants prevent an early art edge from
        // being missed; the reference corpus itself is not copied to the device.
        val TEMPLATES = listOf(
            Template("normal", .10, .11, .90, .53),
            Template("antiguo", .14, .11, .86, .53),
            Template("blanco", .11, .08, .89, .53),
            Template("arte bajo", .14, .14, .86, .56)
        )
    }
}
