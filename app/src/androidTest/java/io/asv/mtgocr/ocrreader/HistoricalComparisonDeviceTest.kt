package io.asv.mtgocr.ocrreader

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HistoricalComparisonDeviceTest {
    @Test fun realHistoricalCatalogMatchesWithoutCollectionWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val index = ArtPrintingIndex.read(context.assets.open(ArtPrintingIndex.ASSET))
        val directory = File(context.noBackupFilesDir, "rules_replay/title_v9")
        val report = StringBuilder()
        data class Case(val prefix: String, val set: String, val number: String, val expected: HistoricalPrintingComparison.Match)
        val files = directory.listFiles().orEmpty().toList() + File(directory.parentFile, "historical_v7").listFiles().orEmpty().toList()
        for ((prefix, set, number, expected) in listOf(
            Case("1790253080871", "SCG", "129", HistoricalPrintingComparison.Match.MATCH),
            Case("1790254970068", "TSP", "17", HistoricalPrintingComparison.Match.MATCH),
            Case("1790257519929", "SCG", "129", HistoricalPrintingComparison.Match.UNKNOWN),
            Case("1790257475776", "TSP", "17", HistoricalPrintingComparison.Match.MATCH))) {
            val file = checkNotNull(files.singleOrNull { it.name.startsWith(prefix) })
            val json = JSONObject(file.readText()); val array = json.getJSONArray("ocrLines")
            val lines = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i); val b = item.getJSONArray("box")
                PrintingOcrLine(item.getInt("pass"), item.getString("text"), b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat())
            }
            val arts = json.getJSONArray("artCandidates")
            val rows = (0 until arts.length()).map { i ->
                val art = arts.getJSONObject(i); val id = art.getString("illustrationId")
                RulesAutoAddPolicy.Artwork(id, art.getString("name"), art.getInt("phash"), art.getInt("dhash"), index.variants(id))
            }
            val names = json.getJSONArray("matchedNames").let { a -> (0 until a.length()).map { a.getString(it) } }
            val foot = StructuredPrintingEvidence.read(lines, emptySet())
            if (prefix == "1790257475776") {
                assertEquals(listOf("17"), foot.historical.collectorNumbers.values)
                assertEquals(ScanReadState.CONFLICT, foot.historical.printedTotals.state)
                assertEquals(ScanReadState.CONFLICT, foot.historical.fractions.state)
            }
            val comparison = HistoricalPrintingComparison.evaluate(rows, names, foot, mapOf(set to "expansion"))
            report.appendLine("$prefix: $comparison")
            File(directory.parentFile, "historical_comparison_v14.txt").writeText(report.toString())
            assertEquals("REVIEW_ONLY", comparison.state)
            val matching = comparison.candidates.filter { it.set == set && it.number == number }
            assertTrue("$prefix: ${comparison.candidates}", matching.isNotEmpty())
            assertTrue(matching.all { it.numberMatch == expected && it.releaseYearMatch == HistoricalPrintingComparison.Match.MATCH })
        }
    }
    @Test fun artistReferenceMatchesRecentRealCapturesAndPreservesOcrConflict() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = android.os.SystemClock.elapsedRealtime()
        val artists = PrintingArtistIndex.read(context.assets.open(PrintingArtistIndex.ASSET),
            PrintingArtistIndex.sha256(context.assets.open(ArtPrintingIndex.ASSET)))
        val loadMs = android.os.SystemClock.elapsedRealtime() - started
        val index = ArtPrintingIndex.read(context.assets.open(ArtPrintingIndex.ASSET))
        val dir = File(context.noBackupFilesDir, "rules_replay/artist_v11")
        val report = StringBuilder("loadAndBindMs=$loadMs\n")
        for ((prefix, expected) in listOf("1790265667603" to HistoricalPrintingComparison.Match.MATCH,
            "1790265738889" to HistoricalPrintingComparison.Match.OCR_CONFLICT)) {
            val file = checkNotNull(dir.listFiles()?.singleOrNull { it.name.startsWith(prefix) })
            val json = JSONObject(file.readText()); val array = json.getJSONArray("ocrLines")
            val lines = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i); val b = item.getJSONArray("box")
                PrintingOcrLine(item.getInt("pass"), item.getString("text"), b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat())
            }
            val rows = json.getJSONArray("artCandidates").let { a -> (0 until a.length()).map { i ->
                val art = a.getJSONObject(i); val id = art.getString("illustrationId")
                RulesAutoAddPolicy.Artwork(id, art.getString("name"), art.getInt("phash"), art.getInt("dhash"), index.variants(id))
            } }
            val references = buildMap { rows.forEach { row -> row.variants.forEach { variant ->
                artists.artist(variant.printingUuid, row.id!!)?.let { put(PrintingArtistIndex.key(variant.printingUuid, row.id), it) }
            } } }
            val names = json.getJSONArray("matchedNames").let { a -> (0 until a.length()).map { a.getString(it) } }
            val actual = HistoricalPrintingComparison.evaluate(rows, names, StructuredPrintingEvidence.read(lines, emptySet()), emptyMap(), references)
            assertEquals(1, actual.candidates.size)
            assertEquals(expected, actual.candidates.single().artistMatch)
            assertEquals(if (prefix == "1790265667603") "Wayne England" else "Alex Horley-Orlandelli", actual.candidates.single().artist)
            report.appendLine("$prefix: $actual")
        }
        File(dir.parentFile, "artist_v11_report.txt").writeText(report.toString())
    }
}
