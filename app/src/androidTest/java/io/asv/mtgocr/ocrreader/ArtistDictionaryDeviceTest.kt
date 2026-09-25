package io.asv.mtgocr.ocrreader

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ArtistDictionaryDeviceTest {
    @Test fun replayThreePhysicalTwilightScansWithGlobalDictionary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val index = PrintingArtistIndex.read(context.assets.open(PrintingArtistIndex.ASSET),
            PrintingArtistIndex.sha256(context.assets.open("art_printing_index.bin")))
        val files = File(context.noBackupFilesDir, "rules_replay/artist_v16").listFiles().orEmpty()
            .filter { it.extension == "json" }
        assertEquals(3, files.size)
        val started = android.os.SystemClock.elapsedRealtime()
        val report = StringBuilder()
        for (file in files) {
            val json = JSONObject(file.readText()); val array = json.getJSONArray("ocrLines")
            val lines = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i); val b = item.getJSONArray("box")
                PrintingOcrLine(item.getInt("pass"), item.getString("text"), b.getDouble(0).toFloat(),
                    b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat())
            }
            val raw = HistoricalFooterEvidence.read(lines)
            val normalized = index.dictionary.normalize(raw.artists)
            assertEquals(listOf("Jason Chan"), normalized.values)
            assertEquals(ScanReadState.READ, normalized.state)
            assertEquals(raw.artists.observations.map { it.source }, normalized.observations.map { it.source })
            if (file.name.startsWith("1790326078112")) {
                assertEquals(ScanReadState.CONFLICT, raw.artists.state)
                assertTrue(normalized.observations.any { it.originalValue == "z Jason Chan" })
            }
            report.appendLine("${file.name}: ${raw.artists.values} -> ${normalized.values}")
        }
        report.appendLine("Three JSON parses + dictionary: ${android.os.SystemClock.elapsedRealtime() - started}ms")
        File(context.noBackupFilesDir, "rules_replay/artist_v16_report.txt").writeText(report.toString())
    }
}
