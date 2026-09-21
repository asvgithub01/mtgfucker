package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.LruCache
import com.caverock.androidsvg.SVG
import io.asv.mtgocr.ocrreader.data.ScryfallImageDataProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

data class SetSymbolShapeMatch(
    val distanceBySetCode: Map<String, Double>,
    val comparedSets: Int,
    val reliable: Boolean
)

/**
 * Matches the printed expansion glyph against Scryfall's vector glyphs.
 *
 * The input is a complete, perspective-corrected card. A deliberately wide band is searched so
 * showcase and borderless frames do not need the symbol at one exact coordinate. Comparison uses
 * edge chamfer distance plus normalized correlation, making rarity colour and illumination mostly
 * irrelevant. This path is opt-in for the new scanner and keeps the maintained scanner unchanged.
 */
class SetSymbolShapeMatcher(
    context: Context,
    private val client: OkHttpClient
) {
    private val symbolDirectory = File(context.filesDir, "set_symbols").apply { mkdirs() }
    private val locks = ConcurrentHashMap<String, Any>()
    private val masks = object : LruCache<String, Bitmap>(MEMORY_MASKS) {}
    private val referenceExecutor = Executors.newFixedThreadPool(REFERENCE_WORKERS)

    fun match(card: Bitmap, cardBounds: Rect, setCodes: Collection<String>): SetSymbolShapeMatch {
        val candidates = setCodes.asSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_COMPARED_SETS)
            .toList()
        if (candidates.isEmpty()) return SetSymbolShapeMatch(emptyMap(), 0, false)

        val searchCrop = cropSearchBand(card, cardBounds)
        val search = prepareSearch(searchCrop)
        searchCrop.recycle()
        val distances = LinkedHashMap<String, Double>()
        try {
            val jobs = candidates.associateWith { code ->
                referenceExecutor.submit<Double?> {
                    val mask = runCatching { referenceMask(code) }.getOrNull() ?: return@submit null
                    runCatching { compare(search, mask) }.getOrNull()
                }
            }
            for ((code, job) in jobs) {
                try {
                    job.get()?.let { distances[code.uppercase(Locale.US)] = it }
                } catch (interrupted: InterruptedException) {
                    jobs.values.forEach { it.cancel(true) }
                    Thread.currentThread().interrupt()
                    break
                } catch (_: ExecutionException) {
                    // A missing or malformed set SVG must not cancel all remaining comparisons.
                }
            }
        } finally {
            search.close()
        }
        val ranked = distances.values.sorted()
        val best = ranked.firstOrNull()
        val runnerUp = ranked.getOrNull(1)
        val reliable = best != null && best <= MAX_RELIABLE_DISTANCE &&
            (runnerUp == null || runnerUp - best >= MIN_WINNING_MARGIN)
        return SetSymbolShapeMatch(distances, distances.size, reliable)
    }

    /** Release the owned reference workers after the last match has completed. */
    fun close() { referenceExecutor.shutdown() }

    private fun cropSearchBand(bitmap: Bitmap, card: Rect): Bitmap {
        val left = (card.left + card.width() * .58f).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (card.top + card.height() * .40f).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (card.left + card.width() * .995f).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (card.top + card.height() * .70f).roundToInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun prepareSearch(bitmap: Bitmap): SearchEdges {
        val rgba = Mat()
        val gray = Mat()
        val enhanced = Mat()
        val blurred = Mat()
        val canny = Mat()
        val threshold = Mat()
        val thresholdEdges = Mat()
        val edges = Mat()
        val inverted = Mat()
        val distance = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.createCLAHE(2.4, Size(6.0, 6.0)).apply(gray, enhanced)
            Imgproc.GaussianBlur(enhanced, blurred, Size(3.0, 3.0), 0.0)
            Imgproc.Canny(blurred, canny, 38.0, 118.0)
            Imgproc.adaptiveThreshold(
                enhanced,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                21,
                6.0
            )
            Imgproc.morphologyEx(threshold, thresholdEdges, Imgproc.MORPH_GRADIENT, kernel)
            Core.bitwise_or(canny, thresholdEdges, edges)
            Core.bitwise_not(edges, inverted)
            Imgproc.distanceTransform(inverted, distance, Imgproc.DIST_L2, 3)
            val edgeFloat = Mat()
            edges.convertTo(edgeFloat, CvType.CV_32F, 1.0 / 255.0)
            return SearchEdges(distance.clone(), edgeFloat)
        } finally {
            rgba.release()
            gray.release()
            enhanced.release()
            blurred.release()
            canny.release()
            threshold.release()
            thresholdEdges.release()
            edges.release()
            inverted.release()
            distance.release()
            kernel.release()
        }
    }

    private fun compare(search: SearchEdges, mask: Bitmap): Double {
        val maskRgba = Mat()
        val maskGray = Mat()
        Utils.bitmapToMat(mask, maskRgba)
        Imgproc.cvtColor(maskRgba, maskGray, Imgproc.COLOR_RGBA2GRAY)
        maskRgba.release()
        var best = 1.0
        try {
            for (targetMaxSide in TEMPLATE_SIZES) {
                val scale = targetMaxSide / max(maskGray.cols(), maskGray.rows()).toDouble()
                val width = max(5, (maskGray.cols() * scale).roundToInt())
                val height = max(5, (maskGray.rows() * scale).roundToInt())
                if (width >= search.distance.cols() || height >= search.distance.rows()) continue
                val resized = Mat()
                val templateEdges = Mat()
                val templateFloat = Mat()
                val gradientKernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_ELLIPSE,
                    Size(3.0, 3.0)
                )
                try {
                    Imgproc.resize(maskGray, resized, Size(width.toDouble(), height.toDouble()), 0.0, 0.0,
                        Imgproc.INTER_AREA)
                    Imgproc.threshold(resized, resized, 80.0, 255.0, Imgproc.THRESH_BINARY)
                    Imgproc.morphologyEx(resized, templateEdges, Imgproc.MORPH_GRADIENT, gradientKernel)
                    val edgeCount = Core.countNonZero(templateEdges)
                    if (edgeCount < MIN_TEMPLATE_EDGE_PIXELS) continue
                    templateEdges.convertTo(templateFloat, CvType.CV_32F, 1.0 / 255.0)

                    val chamferResult = Mat()
                    val correlationResult = Mat()
                    try {
                        Imgproc.matchTemplate(
                            search.distance,
                            templateFloat,
                            chamferResult,
                            Imgproc.TM_CCORR
                        )
                        Imgproc.matchTemplate(
                            search.edges,
                            templateFloat,
                            correlationResult,
                            Imgproc.TM_CCORR_NORMED
                        )
                        val averageEdgeDistance = Core.minMaxLoc(chamferResult).minVal / edgeCount
                        val correlation = Core.minMaxLoc(correlationResult).maxVal.coerceIn(0.0, 1.0)
                        val normalizedChamfer = (averageEdgeDistance /
                            (2.5 + targetMaxSide * .075)).coerceIn(0.0, 1.0)
                        val distance = normalizedChamfer * CHAMFER_WEIGHT +
                            (1.0 - correlation) * CORRELATION_WEIGHT
                        if (distance < best) best = distance
                    } finally {
                        chamferResult.release()
                        correlationResult.release()
                    }
                } finally {
                    resized.release()
                    templateEdges.release()
                    templateFloat.release()
                    gradientKernel.release()
                }
            }
        } finally {
            maskGray.release()
        }
        return best.coerceIn(0.0, 1.0)
    }

    private fun referenceMask(code: String): Bitmap? {
        synchronized(masks) { masks.get(code)?.let { return it } }
        synchronized(locks.getOrPut(code) { Any() }) {
            synchronized(masks) { masks.get(code)?.let { return it } }
            val file = File(symbolDirectory, "$code.svg")
            if (!file.isFile || file.length() == 0L) download(code, file)
            val svg = file.inputStream().buffered().use(SVG::getFromInputStream)
            val rendered = Bitmap.createBitmap(REFERENCE_SIZE, REFERENCE_SIZE, Bitmap.Config.ARGB_8888)
            svg.documentWidth = REFERENCE_SIZE.toFloat()
            svg.documentHeight = REFERENCE_SIZE.toFloat()
            svg.renderToCanvas(Canvas(rendered))
            val mask = trimAlphaMask(rendered)
            rendered.recycle()
            if (mask != null) synchronized(masks) { masks.put(code, mask) }
            locks.remove(code)
            return mask
        }
    }

    private fun download(code: String, destination: File) {
        val temporary = File(symbolDirectory, "$code.matcher.part")
        val request = Request.Builder()
            .url("https://svgs.scryfall.io/sets/$code.svg")
            .header("User-Agent", ScryfallImageDataProvider.USER_AGENT)
            .header("Accept", "image/svg+xml")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.byteStream()?.use { input ->
                temporary.outputStream().buffered().use(input::copyTo)
            } ?: error("SVG vacío")
        }
        check(temporary.renameTo(destination) || runCatching {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }.isSuccess) { "No se pudo guardar el símbolo $code" }
    }

    private fun trimAlphaMask(source: Bitmap): Bitmap? {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        var left = source.width
        var top = source.height
        var right = -1
        var bottom = -1
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                if (Color.alpha(pixels[y * source.width + x]) < MIN_ALPHA) continue
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left || bottom < top) return null
        left = (left - MASK_PADDING).coerceAtLeast(0)
        top = (top - MASK_PADDING).coerceAtLeast(0)
        right = (right + MASK_PADDING).coerceAtMost(source.width - 1)
        bottom = (bottom + MASK_PADDING).coerceAtMost(source.height - 1)
        val width = right - left + 1
        val height = bottom - top + 1
        val output = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = Color.alpha(pixels[(top + y) * source.width + left + x])
                val value = if (alpha >= MIN_ALPHA) 255 else 0
                output[y * width + x] = Color.rgb(value, value, value)
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    private data class SearchEdges(val distance: Mat, val edges: Mat) {
        fun close() {
            distance.release()
            edges.release()
        }
    }

    companion object {
        internal fun fuseDistance(hashDistance: Double, shapeDistance: Double?, reliable: Boolean): Double {
            if (!reliable || shapeDistance == null) return hashDistance.coerceIn(0.0, 1.0)
            return (shapeDistance.coerceIn(0.0, 1.0) * SHAPE_WEIGHT +
                hashDistance.coerceIn(0.0, 1.0) * HASH_WEIGHT).coerceIn(0.0, 1.0)
        }

        private const val REFERENCE_SIZE = 128
        private const val MEMORY_MASKS = 96
        private const val MAX_COMPARED_SETS = 48
        private const val REFERENCE_WORKERS = 4
        private const val MIN_ALPHA = 32
        private const val MASK_PADDING = 2
        private const val MIN_TEMPLATE_EDGE_PIXELS = 10
        private const val CHAMFER_WEIGHT = .72
        private const val CORRELATION_WEIGHT = .28
        private const val SHAPE_WEIGHT = .78
        private const val HASH_WEIGHT = .22
        private const val MAX_RELIABLE_DISTANCE = .58
        private const val MIN_WINNING_MARGIN = .035
        private val TEMPLATE_SIZES = intArrayOf(16, 20, 24, 28, 32, 37, 42, 48, 55, 63)
    }
}
