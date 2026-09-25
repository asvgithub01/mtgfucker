package io.asv.mtgocr.ocrreader

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HistoricalFooterDeviceTest {
    @Test fun replayHistoricalJpegsWithoutCollectionWrites() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir, "rules_replay")
        for ((file, expected) in listOf("pacifism_old.jpg" to "1996", "beckon_old.jpg" to "2013")) {
            val bitmap = checkNotNull(BitmapFactory.decodeFile(File(dir, file).path))
            val reader = PrintingLineOcr()
            val done = CountDownLatch(1)
            var result: PrintingLineOcrResult? = null
            var error: Throwable? = null
            try {
                reader.recognize(bitmap, retainSpatialLines = true) { value, failure ->
                    result = value; error = failure; done.countDown()
                }
                assertTrue(done.await(60, TimeUnit.SECONDS)); assertNull(error)
                val actual = checkNotNull(result)
                val parsed = StructuredPrintingEvidence.read(actual.spatialLines, emptySet())
                File(dir, "$file.fields.txt").writeText(parsed.historical.toString())
                assertEquals(11, actual.attemptedVariants)
                assertTrue("$file: ${parsed.historical.years.values}", expected in parsed.historical.years.values)
                assertTrue(parsed.historical.artists.values.isNotEmpty())
                assertEquals(ScanReadState.PARTIAL, parsed.state)
                assertNull(parsed.footer)
                actual.preview.recycle()
            } finally { reader.close(); bitmap.recycle() }
        }
    }

    @Test fun replaySavedMetadataWithFieldProvenance() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir, "rules_replay/historical_metadata")
        val files = checkNotNull(dir.listFiles()).filter { it.extension == "json" }
        assertEquals(30, files.size)
        var partial = 0
        val report = StringBuilder()
        files.sortedBy { it.name }.forEach { file ->
            val json = JSONObject(file.readText())
            val array = json.getJSONArray("ocrLines")
            val lines = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i); val box = item.getJSONArray("box")
                PrintingOcrLine(item.getInt("pass"), item.getString("text"), box.getDouble(0).toFloat(),
                    box.getDouble(1).toFloat(), box.getDouble(2).toFloat(), box.getDouble(3).toFloat())
            }
            val fields = HistoricalFooterEvidence.read(lines)
            if (fields.hasEvidence) partial++
            assertTrue(fields.years.observations.all { it.source in lines })
            report.appendLine("${file.name}: years=${fields.years.values} artists=${fields.artists.values} fractions=${fields.fractions.values}")
        }
        File(dir.parentFile, "historical_metadata_report.txt").writeText("partial=$partial/${files.size}\n$report")
        assertTrue("Must recover partial evidence from real pre-M15 corpus", partial >= 10)
    }
    @Test fun replayV7FormatsWithoutCollectionWrites() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir, "rules_replay/historical_v7")
        val files = checkNotNull(dir.listFiles()).filter { it.extension == "json" }
        assertEquals(30, files.size)
        val report = StringBuilder()
        files.forEach { file ->
            val json = JSONObject(file.readText()); val array = json.getJSONArray("ocrLines")
            val lines = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i); val box = item.getJSONArray("box")
                PrintingOcrLine(item.getInt("pass"), item.getString("text"), box.getDouble(0).toFloat(),
                    box.getDouble(1).toFloat(), box.getDouble(2).toFloat(), box.getDouble(3).toFloat())
            }
            val fields = HistoricalFooterEvidence.read(lines)
            if (file.name.startsWith("1790252929450")) {
                assertEquals(ScanReadState.READ, fields.years.state)
                assertTrue("David Day missing: ${fields.artists.values}", "David Day" in fields.artists.values)
                assertTrue("60/180" in fields.fractions.values)
            }
            if (file.name.startsWith("1790253080871")) assertEquals(ScanReadState.READ, fields.years.state)
            if (file.name.startsWith("1790253024129")) assertEquals(ScanReadState.CONFLICT, fields.fractions.state)
            // All raw observations must remain traceable, including conflicting readings.
            assertTrue(fields.years.observations.all { it.source in lines })
            report.appendLine("${file.name}: years=${fields.years.values} state=${fields.years.state} artists=${fields.artists.values} fraction=${fields.fractions.values}")
        }
        File(dir.parentFile, "historical_v8_report.txt").writeText(report.toString())
    }
    @Test fun replayDifficultV14MetadataRecoversCopyrightWithoutInventingCollectors() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir, "rules_replay")
        val files = File(root, "difficult_v14").listFiles().orEmpty().filter { it.extension == "json" }
        assertEquals(30, files.size)
        val report = StringBuilder()
        for (file in files.sortedBy { it.name }) {
            val json = JSONObject(file.readText())
            val a = json.getJSONArray("ocrLines")
            val lines = (0 until a.length()).map { i ->
                val item = a.getJSONObject(i); val b = item.getJSONArray("box")
                PrintingOcrLine(item.getInt("pass"), item.getString("text"), b.getDouble(0).toFloat(),
                    b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat())
            }
            val read = HistoricalFooterEvidence.read(lines)
            if (file.name.startsWith("1790321998807")) {
                assertEquals(listOf("1993–2009"), read.years.values)
                assertEquals(listOf("Jason Chan"), read.artists.values)
                assertEquals(ScanReadState.READ, read.years.state)
                assertTrue(read.collectorNumbers.values.isEmpty())
            }
            val previousFractions = json.getJSONObject("historicalFooter").getJSONObject("fractions").getJSONArray("observations")
            val previousValues = (0 until previousFractions.length()).map { previousFractions.getJSONObject(it).getString("value") }.distinct()
            assertEquals(file.name, previousValues, read.fractions.values)
            report.appendLine("${file.name}: year=${read.years.values} ${read.years.state}, artist=${read.artists.values} ${read.artists.state}")
        }
        File(root, "copyright_v15_report.txt").writeText(report.toString())
    }
}
