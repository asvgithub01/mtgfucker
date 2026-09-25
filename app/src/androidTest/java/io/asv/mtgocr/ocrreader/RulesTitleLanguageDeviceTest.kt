package io.asv.mtgocr.ocrreader

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RulesTitleLanguageDeviceTest {
    @Test fun latestThirtyCapturesRecoverPortugueseWithoutNewOcrOrCollectionWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = android.os.SystemClock.elapsedRealtime()
        val index = TitleLanguageIndex.read(context.assets.open(TitleLanguageIndex.ASSET))
        val report = StringBuilder("loadMs=${android.os.SystemClock.elapsedRealtime() - started}\n")
        val files = File(context.noBackupFilesDir, "rules_replay/language_v12").listFiles().orEmpty().filter { it.extension == "json" }
        assertEquals(30, files.size)
        var recovered = 0; var flickering = 0
        val lookupStart = android.os.SystemClock.elapsedRealtime()
        for (file in files.sortedBy { it.name }) {
            val json = JSONObject(file.readText())
            fun strings(key: String) = json.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
            val names = strings("matchedNames")
            val evidence = index.lookup(strings("rawTitle"), names)
            val decision = RulesScanPolicy.evaluate(RulesScanPolicy.Input(names, emptyList(), emptyList(),
                StructuredPrintingRead(ScanReadState.UNREADABLE), json.optString("observedLanguage"),
                json.optDouble("rulesLanguageConfidence", 0.0).toFloat(), json.optString("rulesText"), evidence))
            if (names == listOf("Flickering Spirit")) {
                flickering++
                assertEquals(file.name, setOf("pt"), evidence.candidates)
                assertEquals(file.name, "pt", decision.language)
                if (json.optString("observedLanguage").isBlank()) recovered++
            }
            if (names.size != 1) assertTrue(evidence.matches.isEmpty())
            report.appendLine("${file.name}: ${names} ${json.optString("observedLanguage")} -> ${decision.language}; title=${evidence.candidates}")
        }
        assertEquals(14, flickering); assertEquals(9, recovered)
        report.appendLine("30lookupsMs=${android.os.SystemClock.elapsedRealtime() - lookupStart}; recovered=$recovered")
        File(context.noBackupFilesDir, "rules_replay/language_v12_report.txt").writeText(report.toString())
        val shared = index.lookup(listOf("Elfos de Llanowar"), listOf("Llanowar Elves"))
        assertTrue(shared.candidates.containsAll(listOf("es", "pt")))
    }

    @Test fun localizedSelectionPreservesPrintingFinishAndEvidenceLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val titles = TitleLanguageIndex.read(context.assets.open(TitleLanguageIndex.ASSET))
        val index = ArtPrintingIndex.read(context.assets.open(ArtPrintingIndex.ASSET))
        val file = File(context.noBackupFilesDir, "rules_replay/language_v12").listFiles()!!.first { it.name.startsWith("1790267003534") }
        val json = JSONObject(file.readText()); val first = json.getJSONArray("artCandidates").getJSONObject(0)
        val art = first.getString("illustrationId")
        val variants = index.variants(art)
        val variant = variants.first { it.cardName == "Flickering Spirit" && it.languageCode == "pt" }
        val row = HashScanAnalysis.Row(ArtHashMatcher.Candidate(ArtHashIndex.Hit("test", art,
            "Flickering Spirit", "TSP", "17", 4, 4), "test"), emptyList(), variants)
        val result = HashScanAnalysis.Result(null, listOf(row), listOf("Espírito Flutuante"), listOf("Flickering Spirit"),
            null, null, null, null, "pt", emptyList(), 0, 0, 0, 0, 0, byteArrayOf(1),
            titleLanguage = titles.lookup(listOf("Espírito Flutuante"), listOf("Flickering Spirit")))
        val original = variant.toEditionOption().copy(displayName = "Flickering Spirit", finish = "foil", isFoil = true)
        val localized = RulesScanReport.localizedOption(result, original, "pt")
        assertEquals("Espírito Flutuante", localized.displayName)
        assertEquals(original.printingUuid, localized.printingUuid)
        assertEquals("foil", localized.finish)
        val saved = JSONObject(RulesScanReport.capture(result, localized, "pt")!!.metadataJson)
        assertEquals("pt", saved.getString("effectiveLanguage"))
        assertEquals("pt", saved.getJSONObject("rulesScanner").getJSONObject("titleLanguage").getJSONArray("candidates").getString(0))
        assertNotEquals("Espírito Flutuante", RulesScanReport.localizedOption(result, original, "es").displayName)
    }
}
