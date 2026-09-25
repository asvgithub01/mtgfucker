package io.asv.mtgocr.ocrreader

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HashEditionPickerDeviceTest {
    @Test fun realSpgUniqueArtworkResolvesWithV2DespiteFailedSymbolAndWithoutOcr() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "two_stage_replay/spg.jpg")
        assumeTrue("Private SPG corpus missing", file.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val analysis = HashScanAnalysis(context)
        try {
            val prepared = CountDownLatch(1)
            analysis.prepare(false) { assertTrue(it); prepared.countDown() }
            assertTrue(prepared.await(30, TimeUnit.SECONDS))
            for (width in listOf(630, 1260)) {
                val decoded = checkNotNull(BitmapFactory.decodeFile(file.path))
                val frame = Bitmap.createScaledBitmap(decoded, width, width * 88 / 63, true)
                if (frame !== decoded) decoded.recycle()
                val complete = CountDownLatch(1)
                var result: HashScanAnalysis.Result? = null
                analysis.analyze(frame, HashScanAnalysis.Options(ocr = false, symbol = false,
                    symbolV2 = true, symbolRetryAttempt = 1)) { result = it; complete.countDown() }
                assertTrue(complete.await(45, TimeUnit.SECONDS))
                val actual = checkNotNull(result)
                assertTrue(actual.errors.toString(), actual.errors.isEmpty())
                val row = actual.rows.first()
                assertEquals("Archmage Emeritus", row.candidate.hit.name)
                assertEquals(listOf("SPG"), row.variants.map { it.set.code }.distinct())
                assertNull("Fixture must exercise unsuccessful symbol", actual.symbolV2?.selectedSet)
                assertTrue("$width: ${row.candidate.hit}", row.resolvedByUniqueArtwork)
                assertEquals("SPG", row.resolvedVariant?.set?.code)
                assertEquals("en", row.resolvedVariant?.languageCode)
                assertTrue(HashAutoAddPolicy.acceptsTopHit(row.candidate.hit.phashDistance,
                    row.resolvedEdition != null, row.resolvedVariant != null))
                assertTrue(actual.rows.drop(1).all { it.resolvedVariant == null })
                assertTrue(frame.isRecycled)
            }
        } finally { analysis.close() }
    }

    @Test fun gridFitsNarrowScreenAndCanScrollManySets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, android.R.style.Theme_Material_Light)
            val density = context.resources.displayMetrics.density
            val sets = (1..18).map { ArtPrintingIndex.SetInfo("S$it", "Collection number $it", "2026", "", null, null) }
            val panel = HashEditionGridPanel(context, sets, selected = {}, loadSymbol = { _, target ->
                target.setImageResource(android.R.drawable.btn_star_big_on)
            })
            val width = (320 * density).toInt(); val height = (340 * density).toInt()
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            panel.layout(0, 0, width, height)
            assertEquals(width, panel.width)
            assertTrue(panel.getChildAt(0).height > panel.height)
            assertTrue(panel.tiles.values.all { it.width > 0 && it.right <= width })
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE); panel.draw(canvas)
                File(instrumentation.targetContext.noBackupFilesDir, "edition-grid-preview.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { bitmap.recycle() }
        }
    }

    @Test fun gridListsDistinctSetsAndOnlyDeliversOneExplicitChoiceEvenWithoutSymbols() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, android.R.style.Theme_Material_Light)
            fun set(code: String) = ArtPrintingIndex.SetInfo(code, "Set $code", "2026-01-01", code, null, null)
            val calls = mutableListOf<String>()
            val panel = HashEditionGridPanel(context, listOf(set("FUT"), set("TSR"), set("FUT")),
                selected = { calls += it }, loadSymbol = { _, _ -> })
            assertEquals(setOf("FUT", "TSR"), panel.tiles.keys)
            assertTrue(calls.isEmpty()) // Opening or abandoning the picker cannot add anything.
            assertEquals("Set FUT (FUT)", panel.tiles.getValue("FUT").contentDescription)
            panel.tiles.getValue("FUT").performClick()
            panel.tiles.getValue("TSR").performClick()
            panel.tiles.getValue("FUT").performClick()
            assertEquals(listOf("FUT"), calls)
        }
    }

    @Test fun realMyrAndSpgCapturesRequireOcrIdentityBeforeEditionResolution() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "two_stage_replay")
        assumeTrue("Private Myr/SPG corpus missing", File(directory, "myr.jpg").isFile && File(directory, "spg.jpg").isFile)
        assertTrue(OpenCVLoader.initLocal())
        val analysis = HashScanAnalysis(context)
        try {
            val prepared = CountDownLatch(1)
            var ready = false
            analysis.prepare(true) { ready = it; prepared.countDown() }
            assertTrue(prepared.await(60, TimeUnit.SECONDS)); assertTrue(ready)
            for ((file, expected) in listOf("myr.jpg" to "Sarcomite Myr", "spg.jpg" to "Archmage Emeritus")) {
                for (width in listOf(630, 1260)) for (symbol in listOf(false, true)) {
                    val decoded = checkNotNull(BitmapFactory.decodeFile(File(directory, file).path))
                    val frame = if (decoded.width == width) decoded else Bitmap.createScaledBitmap(decoded, width, width * 88 / 63, true).also { decoded.recycle() }
                    val complete = CountDownLatch(1)
                    var result: HashScanAnalysis.Result? = null
                    analysis.analyze(frame, HashScanAnalysis.Options(ocr = false, symbol = true,
                        symbolV2 = symbol, editionPicker = true)) { result = it; complete.countDown() }
                    assertTrue(complete.await(45, TimeUnit.SECONDS))
                    val actual = checkNotNull(result)
                    assertTrue(actual.errors.toString(), actual.errors.isEmpty())
                    assertTrue(actual.options.ocr); assertFalse(actual.options.symbol)
                    val index = HashEditionResolutionPolicy.identityIndex(actual.rows.map { it.candidate.hit.name }, actual.names)
                    assertNotNull("$file names=${actual.names} candidates=${actual.rows.map { it.candidate.hit.name }}", index)
                    val row = actual.rows[checkNotNull(index)]
                    assertEquals(expected, row.candidate.hit.name)
                    assertTrue(row.variants.isNotEmpty())
                    assertTrue(actual.rows.filterIndexed { i, _ -> i != index }.all { it.resolvedVariant == null })
                    assertNotNull(actual.capturedJpeg)
                    assertTrue(frame.isRecycled)
                    android.util.Log.i("TwoStageReplay", "$file width=$width symbol=$symbol identity=$expected footer=${actual.printing} resolved=${row.resolvedVariant?.set?.code} sets=${row.variants.map { it.set.code }.distinct()}")
                }
            }
        } finally { analysis.close() }
    }
}
