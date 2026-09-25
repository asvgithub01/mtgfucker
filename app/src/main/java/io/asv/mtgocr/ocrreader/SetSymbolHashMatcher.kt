package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

internal data class SymbolHashScore(
    val setCode: String, val distance: Double, val phashDistance: Int,
    val silhouetteDistance: Double, val crop: List<Int>,
    val queryHash: String, val referenceHash: String,
    val referenceSetCode: String = setCode,
    val detailCorrelation: Double? = null
)

internal data class SetSymbolHashMatch(
    val scores: List<SymbolHashScore> = emptyList(),
    val missingSets: List<String> = emptyList(),
    val selectedSet: String? = null,
    val reason: String,
    val cropPng: ByteArray? = null,
    val elapsedMs: Long = 0,
    val cardName: String? = null,
    val comparedSets: List<String> = emptyList(),
    val noSymbolSets: List<String> = emptyList(),
    val inputWidth: Int = 0, val inputHeight: Int = 0
) {
    fun json(): JSONObject = JSONObject().apply {
        put("algorithm", "symbol-shape-contrast-v2.5")
        put("weights", JSONObject().put("silhouette", .75).put("phash", .15).put("aspect", .10))
        put("cardName", cardName ?: JSONObject.NULL)
        put("comparedSets", JSONArray(comparedSets))
        put("noSymbolSets", JSONArray(noSymbolSets))
        put("minDetailHoleFraction", SetSymbolHashMatcher.MIN_DETAIL_SUPPORT)
        put("detailWeights", JSONObject().put("silhouette", .35).put("contrast", .55).put("aspect", .10))
        put("inputSize", JSONArray(listOf(inputWidth, inputHeight)))
        put("segmentationWidth", SymbolResolutionPolicy.segmentationWidth(inputWidth))
        put("searchBandRelative", JSONArray(listOf(.60, .43, .995, .71)))
        put("selectedSet", selectedSet ?: JSONObject.NULL)
        put("reason", reason)
        put("elapsedMs", elapsedMs)
        put("maxDistance", SetSymbolHashPolicy.MAX_DISTANCE)
        put("minMargin", SetSymbolHashPolicy.MIN_MARGIN)
        put("missingSets", JSONArray(missingSets))
        put("scores", JSONArray().also { list -> scores.forEach { score ->
            list.put(JSONObject().apply {
                put("set", score.setCode)
                put("referenceSet", score.referenceSetCode)
                put("detailCorrelation", score.detailCorrelation ?: JSONObject.NULL)
                put("distance", score.distance)
                put("phashDistance", score.phashDistance)
                put("silhouetteDistance", score.silhouetteDistance)
                put("cropXYWH", JSONArray(score.crop))
                put("queryHash", score.queryHash)
                put("referenceHash", score.referenceHash)
            })
        } })
    }
}

/** V2: isolate the glyph, preserve its ink details, then compare hashes within ONE card's sets.
 * Unlike V1's broad-band template search, both distances refer to the same isolated crop.
 * Unusual layouts, absent symbols, missing references and reused glyphs must abstain.
 */
internal class SetSymbolHashMatcher(private val referenceMask: (String) -> Bitmap?) {
    companion object {
        // A tiny hole in a thin lightning bolt cannot carry most of the score at camera resolution.
        // Similar silhouettes still force detail comparison via discrimination() (e.g. M14/M15).
        const val MIN_DETAIL_SUPPORT = .10
    }
    private data class Fingerprint(
        val phash: Long, val pixels: BooleanArray, val aspect: Double,
        val gray: ByteArray, val support: IntArray, val holeFraction: Double
    )
    private data class Query(val fingerprint: Fingerprint, val rect: Rect)
    private val referenceWorkers = Executors.newFixedThreadPool(4)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Fingerprint>()

    fun close() { referenceWorkers.shutdownNow() }

    fun match(
        card: Bitmap,
        setCodes: Collection<String>,
        retainedSymbols: Map<String, List<String>> = emptyMap(),
        language: String = ""
    ): SetSymbolHashMatch {
        val start = SystemClock.elapsedRealtime()
        val allCodes = setCodes.map { it.uppercase(Locale.ROOT) }.distinct()
        val noSymbolCodes = allCodes.filter { PrintedSetSymbolPolicy.mayHaveNoSymbol(it, language) }
        val codes = allCodes.filter { PrintedSetSymbolPolicy.needsPrintedReference(it, language) }
        if (codes.isEmpty()) return SetSymbolHashMatch(
            reason = if (allCodes.isEmpty()) "sin_ediciones" else "simbolo_no_aplicable",
            noSymbolSets = noSymbolCodes, elapsedMs = SystemClock.elapsedRealtime() - start, inputWidth = card.width, inputHeight = card.height)

        val templates = codes.associateWith { code ->
            // PLST does not print the catalog logo in the expansion-symbol position.
            if (code == "PLST") retainedSymbols[code].orEmpty() else listOf(code)
        }
        val queries = extract(card)
        if (queries.isEmpty() || codes.isEmpty()) return SetSymbolHashMatch(
            reason = if (noSymbolCodes.isNotEmpty()) "ausencia_posible_no_confirmada" else "sin_recorte_de_simbolo",
            elapsedMs = SystemClock.elapsedRealtime() - start, comparedSets = codes, noSymbolSets = noSymbolCodes, inputWidth = card.width, inputHeight = card.height
        )
        val missing = ArrayList<String>()
        // A finite budget prevents a cold/offline cache from stalling the scanner indefinitely.
        val deadline = SystemClock.elapsedRealtime() + 6000
        val jobs = templates.values.flatten().distinct().take(64).associateWith { code -> referenceWorkers.submit<Fingerprint?> {
            cache[code] ?: runCatching {
                if (Thread.currentThread().isInterrupted) return@submit null
                val mask = referenceMask(code.lowercase(Locale.ROOT)) ?: return@submit null
                val rgba = Mat()
                val gray = Mat()
                try {
                    Utils.bitmapToMat(mask, rgba)
                    Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                    fingerprint(gray)?.also { cache[code] = it }
                } finally { rgba.release(); gray.release() }
            }.getOrNull()
        } }
        val scores = ArrayList<SymbolHashScore>()
        val alternatives = LinkedHashMap<String, List<SymbolHashScore>>()
        try {
            val loaded = jobs.mapValues { (_, job) -> runCatching {
                if (job.isDone) job.get()
                else job.get((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1), TimeUnit.MILLISECONDS)
            }.getOrNull() }
            val detailed = loaded.mapValues { (_, value) -> value?.let {
                discrimination(it, loaded.values.filterNotNull())
            } }
            codes.forEach { code ->
                val references = templates.getValue(code).map { template -> template to detailed[template] }
                if (references.isEmpty() || references.any { it.second == null }) {
                    missing += code
                } else {
                    val candidates = references.flatMap { (template, loaded) ->
                    val reference = checkNotNull(loaded)
                    queries.map { query ->
                        val q = query.fingerprint
                        val p = java.lang.Long.bitCount(q.phash xor reference.phash)
                        var different = 0
                        var union = 0
                        q.pixels.indices.forEach { i ->
                            if (q.pixels[i] || reference.pixels[i]) union++
                            if (q.pixels[i] != reference.pixels[i]) different++
                        }
                        val silhouette = different.toDouble() / union.coerceAtLeast(1)
                        val aspect = (abs(ln(q.aspect / reference.aspect)) / 1.2).coerceIn(0.0, 1.0)
                        // Metallic cores reverse the contrast of letters/numbers. Use the
                        // photographed interior, not a filled contour that destroys them.
                        val detail = if (reference.holeFraction >= MIN_DETAIL_SUPPORT) contrast(q, reference) else null
                        val distance = if (detail != null) .35 * silhouette + .55 * (1.0 - detail) + .10 * aspect
                            else SetSymbolHashPolicy.distance(silhouette, p, aspect)
                        SymbolHashScore(code, distance,
                            p, silhouette,
                            listOf(query.rect.x, query.rect.y, query.rect.width, query.rect.height),
                            java.lang.Long.toHexString(q.phash), java.lang.Long.toHexString(reference.phash), template, detail)
                    }
                    }
                    alternatives[code] = candidates
                    candidates.minByOrNull { it.distance }?.let(scores::add)
                }
            }
        } finally { jobs.values.forEach { if (!it.isDone) it.cancel(true) } }
        // Resolve location once, then compare EVERY candidate set against that same glyph.
        // Otherwise a hole or an unrelated character can act as a rival expansion symbol.
        val anchor = scores.minByOrNull { it.distance }?.crop
        val ranked = if (anchor == null) emptyList() else alternatives.values.mapNotNull { choices ->
            choices.filter { overlap(it.crop, anchor) >= .55 }.minByOrNull { it.distance }
        }.sortedBy { it.distance }
        var winner = SetSymbolHashPolicy.winner(ranked.associate { it.setCode to it.distance }, missing)
        // Chronicles uses old expansion glyphs, not its catalog SVG. Do not mistake a retained
        // glyph for proof of an original printing (or ignore a possible Chronicles alternative).
        if (codes.any { ChroniclesSymbolPolicy.isChronicles(it) }) winner = null
        val preview = ranked.firstOrNull()?.crop?.let { rect ->
            val crop = Bitmap.createBitmap(card, rect[0], rect[1], rect[2], rect[3])
            try { ByteArrayOutputStream().use {
                crop.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray()
            } } finally { crop.recycle() }
        }
        val reason = when {
            codes.any { ChroniclesSymbolPolicy.isChronicles(it) } -> "simbolo_reutilizado_chronicles"
            missing.isNotEmpty() -> "referencias_incompletas"
            winner != null -> "simbolo_confirmado"
            ranked.isEmpty() -> "sin_recorte_de_simbolo"
            ranked.first().distance > SetSymbolHashPolicy.MAX_DISTANCE -> "distancia_excesiva"
            else -> "margen_insuficiente"
        }
        return SetSymbolHashMatch(ranked, missing, winner, reason, preview,
            SystemClock.elapsedRealtime() - start, comparedSets = codes, noSymbolSets = noSymbolCodes, inputWidth = card.width, inputHeight = card.height)
    }

    private fun extract(card: Bitmap): List<Query> {
        val rgba = Mat()
        val gray = Mat()
        val scaled = Mat()
        val threshold = Mat()
        val edges = Mat()
        val enhanced = Mat()
        val lowEdges = Mat()
        val lowThreshold = Mat()
        val joined = Mat()
        val normalizedWidth = SymbolResolutionPolicy.segmentationWidth(card.width)
        val units = normalizedWidth / 744
        val kernelSize = (units * 2 + 1).toDouble()
        val blockSize = 30 * units + 1
        val minPart = 3 * units; val minGlyph = 10 * units
        val maxWidth = 140 * units; val maxHeight = 80 * units
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(kernelSize, kernelSize))
        val queries = ArrayList<Query>()
        // Normalize scale before segmentation, not only after computing a hash.
        val scale = normalizedWidth.toDouble() / card.width
        val height = (card.height * scale).roundToInt()
        val band = Rect((normalizedWidth * .60).toInt(), (height * .43).toInt(),
            (normalizedWidth * .395).toInt(), (height * .28).toInt())
        try {
            Utils.bitmapToMat(card, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.resize(gray, scaled, Size(normalizedWidth.toDouble(), height.toDouble()))
            val roi = scaled.submat(band)
            try {
                Imgproc.adaptiveThreshold(roi, threshold, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV, blockSize, 7.0)
                Imgproc.Canny(roi, edges, 40.0, 120.0)
                val clahe = Imgproc.createCLAHE(2.5, Size(4.0, 4.0))
                try { clahe.apply(roi, enhanced) } finally { clahe.collectGarbage() }
                Imgproc.Canny(enhanced, lowEdges, 20.0, 60.0)
                Imgproc.adaptiveThreshold(enhanced, lowThreshold, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, blockSize, 3.0)
                // Closing alone can join wide core logos to the type-line frame. Preserve raw
                // contours too; enhanced passes recover faint silver/gold outlines.
                for (source in listOf(threshold, edges, lowEdges, lowThreshold)) for (close in listOf(false, true)) {
                    if (close) Imgproc.morphologyEx(source, joined, Imgproc.MORPH_CLOSE, kernel)
                    else source.copyTo(joined)
                    val contours = ArrayList<MatOfPoint>()
                    val hierarchy = Mat()
                    try {
                        // A type-line/frame contour can enclose the glyph; keep nested contours.
                        Imgproc.findContours(joined, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                        // Core-set numbers/logos can consist of disconnected pieces. Include
                        // nearby groups as well as single contours, without hashing the type line.
                        val parts = contours.filter { contour ->
                            val r = Imgproc.boundingRect(contour)
                            r.width in minPart..maxWidth && r.height in minPart..maxHeight && r.x > units && r.y > units &&
                                r.x + r.width < band.width - units && r.y + r.height < band.height - units
                        }.sortedByDescending { Imgproc.contourArea(it) }.take(80)
                        val groups = ArrayList<List<MatOfPoint>>()
                        parts.forEach { seed ->
                            val group = arrayListOf(seed)
                            groups += group.toList()
                            repeat(5) {
                                val bounds = groupBounds(group)
                                val next = parts.filter { it !in group }.filter {
                                    val r = Imgproc.boundingRect(it)
                                    val gapX = maxOf(0, bounds.x - r.x - r.width, r.x - bounds.x - bounds.width)
                                    val overlapY = minOf(bounds.y + bounds.height, r.y + r.height) - maxOf(bounds.y, r.y)
                                    gapX > 0 && gapX <= maxOf(minPart.toDouble(), maxOf(bounds.height, r.height) * .35) && overlapY > 0 &&
                                        groupBounds(group + it).let { box -> box.width <= maxWidth && box.height <= maxHeight }
                                }.minByOrNull { abs(Imgproc.boundingRect(it).x - bounds.x) }
                                if (next != null) { group += next; groups += group.toList() }
                            }
                        }
                        for (group in groups.distinctBy { groupBounds(it).toString() }.take(80)) {
                            val r = groupBounds(group)
                            if (r.x <= units || r.y <= units || r.x + r.width >= band.width - units ||
                                r.y + r.height >= band.height - units) continue
                            if (r.width !in minGlyph..maxWidth || r.height !in minGlyph..maxHeight) continue
                            if (r.width.toDouble() / r.height !in .3..4.5) continue
                            if (r.x + r.width / 2 < band.width * .48) continue
                            val isolated = Mat.zeros(joined.size(), CvType.CV_8UC1)
                            try {
                                Imgproc.drawContours(isolated, group, -1, Scalar(255.0), Imgproc.FILLED)
                                val x = ((band.x + r.x) / scale).toInt().coerceIn(0, card.width - 1)
                                val y = ((band.y + r.y) / scale).toInt().coerceIn(0, card.height - 1)
                                val w = (r.width / scale).roundToInt().coerceIn(1, card.width - x)
                                val h = (r.height / scale).roundToInt().coerceIn(1, card.height - y)
                                fingerprint(isolated, roi)?.let { queries += Query(it, Rect(x, y, w, h)) }
                            } finally { isolated.release() }
                        }
                    } finally { contours.forEach { it.release() }; hierarchy.release() }
                }
            } finally { roi.release() }
            return queries.distinctBy { it.rect.toString() to it.fingerprint.phash }.take(320)
        } finally {
            rgba.release(); gray.release(); scaled.release(); threshold.release()
            edges.release(); enhanced.release(); lowEdges.release(); lowThreshold.release()
            joined.release(); kernel.release()
        }
    }

    private fun discrimination(reference: Fingerprint, others: List<Fingerprint>): Fingerprint {
        val difference = BooleanArray(4096)
        var detailedFamily = reference.holeFraction >= MIN_DETAIL_SUPPORT
        for (other in others) {
            if (other === reference) continue
            var union = 0; var differing = 0
            for (i in difference.indices) {
                if (reference.pixels[i] || other.pixels[i]) union++
                if (reference.pixels[i] != other.pixels[i]) differing++
            }
            if (differing.toDouble() / union.coerceAtLeast(1) >= .12) continue
            detailedFamily = detailedFamily || other.holeFraction > .04 || reference.holeFraction > .04
            for (i in difference.indices) if (abs((reference.gray[i].toInt() and 255) -
                (other.gray[i].toInt() and 255)) > 100) difference[i] = true
        }
        val support = if (difference.count { it } > 8) reference.support.filter { i ->
            val x = i % 64; val y = i / 64
            (maxOf(0, y - 2)..minOf(63, y + 2)).any { yy ->
                (maxOf(0, x - 2)..minOf(63, x + 2)).any { xx -> difference[yy * 64 + xx] }
            }
        }.toIntArray().takeIf { it.size >= 20 } ?: reference.support else reference.support
        return reference.copy(support = support, holeFraction = if (detailedFamily) maxOf(MIN_DETAIL_SUPPORT, reference.holeFraction) else reference.holeFraction)
    }

    private fun overlap(first: List<Int>, second: List<Int>): Double {
        val width = (minOf(first[0] + first[2], second[0] + second[2]) - maxOf(first[0], second[0])).coerceAtLeast(0)
        val height = (minOf(first[1] + first[3], second[1] + second[3]) - maxOf(first[1], second[1])).coerceAtLeast(0)
        val intersection = width * height
        return intersection.toDouble() / (first[2] * first[3] + second[2] * second[3] - intersection).coerceAtLeast(1)
    }

    private fun contrast(query: Fingerprint, reference: Fingerprint): Double {
        val support = reference.support
        if (support.size < 20) return 0.0
        var best = 0.0
        // One normalized pixel compensates contour rounding, not missing chunks of a glyph.
        for (dy in -1..1) for (dx in -1..1) {
            var sumQ = 0.0; var sumR = 0.0; var qq = 0.0; var rr = 0.0; var qr = 0.0
            for (i in support) {
                val qi = (i / 64 + dy).coerceIn(0, 63) * 64 + (i % 64 + dx).coerceIn(0, 63)
                val q = (query.gray[qi].toInt() and 255).toDouble()
                val r = (reference.gray[i].toInt() and 255).toDouble()
                sumQ += q; sumR += r; qq += q * q; rr += r * r; qr += q * r
            }
            val varQ = (qq - sumQ * sumQ / support.size).coerceAtLeast(0.0)
            val varR = (rr - sumR * sumR / support.size).coerceAtLeast(0.0)
            val value = if (varR < support.size * 9) (1.0 - kotlin.math.sqrt(varQ / support.size) / 32.0)
                else if (varQ < support.size * 9) 0.0
                else abs(qr - sumQ * sumR / support.size) / kotlin.math.sqrt(varQ * varR)
            best = maxOf(best, value.coerceIn(0.0, 1.0))
        }
        return best
    }

    private fun groupBounds(group: List<MatOfPoint>): Rect {
        val boxes = group.map(Imgproc::boundingRect)
        val x = boxes.minOf { it.x }
        val y = boxes.minOf { it.y }
        return Rect(x, y, boxes.maxOf { it.x + it.width } - x, boxes.maxOf { it.y + it.height } - y)
    }

    /** Shared aspect-preserving normalization; silhouette and interior appearance stay separate. */
    private fun fingerprint(mask: Mat, appearance: Mat = mask): Fingerprint? {
        val binary = Mat()
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        val silhouette = Mat.zeros(mask.size(), CvType.CV_8UC1)
        val normalized = Mat.zeros(64, 64, CvType.CV_8UC1)
        val detailImage = Mat.zeros(64, 64, CvType.CV_8UC1)
        val interior = Mat()
        val interiorKernel = Mat.ones(7, 7, CvType.CV_8UC1)
        val floats = Mat()
        val dct = Mat()
        try {
            Imgproc.threshold(mask, binary, 80.0, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            if (contours.isEmpty()) return null
            // Shape and photographed detail are separate signals: filling ONLY the shape
            // makes it robust to rarity colour; the grayscale interior remains untouched.
            Imgproc.drawContours(silhouette, contours, -1, Scalar(255.0), Imgproc.FILLED)
            val points = Mat()
            val bounds = try {
                Core.findNonZero(silhouette, points)
                Imgproc.boundingRect(points)
            } finally { points.release() }
            if (bounds.width < 2 || bounds.height < 2) return null
            val ratio = 56.0 / maxOf(bounds.width, bounds.height)
            val width = (bounds.width * ratio).roundToInt().coerceAtLeast(1)
            val height = (bounds.height * ratio).roundToInt().coerceAtLeast(1)
            val source = silhouette.submat(bounds)
            val target = normalized.submat(Rect((64 - width) / 2, (64 - height) / 2, width, height))
            try { Imgproc.resize(source, target, target.size(), 0.0, 0.0, Imgproc.INTER_AREA) }
            finally { source.release(); target.release() }
            val detailSource = appearance.submat(bounds)
            val detailTarget = detailImage.submat(Rect((64 - width) / 2, (64 - height) / 2, width, height))
            try { Imgproc.resize(detailSource, detailTarget, detailTarget.size(), 0.0, 0.0, Imgproc.INTER_AREA) }
            finally { detailSource.release(); detailTarget.release() }
            Imgproc.threshold(normalized, interior, 128.0, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.erode(interior, interior, interiorKernel)
            val supported = ByteArray(4096)
            val detail = ByteArray(4096)
            interior.get(0, 0, supported)
            detailImage.get(0, 0, detail)
            val support = supported.indices.filter { (supported[it].toInt() and 255) > 0 }.toIntArray()
            val holes = support.count { (detail[it].toInt() and 255) < 128 }.toDouble() / support.size.coerceAtLeast(1)
            normalized.convertTo(floats, CvType.CV_32F)
            Core.dct(floats, dct)
            val values = ArrayList<Float>(63)
            val row = FloatArray(64)
            for (y in 0..7) {
                dct.get(y, 0, row)
                for (x in 0..7) if (x != 0 || y != 0) values += row[x]
            }
            val median = values.sorted()[31]
            var hash = 0L
            values.forEach { hash = (hash shl 1) or if (it > median) 1L else 0L }
            val pixels = ByteArray(4096)
            normalized.get(0, 0, pixels)
            return Fingerprint(hash, BooleanArray(4096) { (pixels[it].toInt() and 255) >= 128 },
                bounds.width.toDouble() / bounds.height, detail, support, holes)
        } finally {
            binary.release(); contours.forEach { it.release() }; hierarchy.release()
            silhouette.release(); normalized.release(); detailImage.release(); interior.release(); interiorKernel.release()
            floats.release(); dct.release()
        }
    }
}
