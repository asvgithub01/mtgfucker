package io.asv.mtgocr.ocrreader

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assume.assumeTrue
import org.opencv.android.OpenCVLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RulesScannerDeviceTest {
    @Test fun realMindRotCatalogueAndDatabaseSelectDifferentEditionsByFooter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val index = ArtPrintingIndex.read(context.assets.open(ArtPrintingIndex.ASSET))
        val art = "4fc53a41-0bd7-4055-b6c1-7ff69175da71"
        val variants = index.variants(art)
        val types = io.asv.mtgocr.ocrreader.data.CardDatabase.get(context).cardDao().magicSets()
            .associate { it.code.uppercase(java.util.Locale.ROOT) to it.type }
        val top = HashScanAnalysis.Row(ArtHashMatcher.Candidate(ArtHashIndex.Hit("test", art,
            "Mind Rot", "KLD", "93", 4, 8), "test"), emptyList(), variants)
        val runnerUp = HashScanAnalysis.Row(ArtHashMatcher.Candidate(ArtHashIndex.Hit("other", "other",
            "Other", "M20", "1", 14, 25), "test"), emptyList())
        for ((set, number) in listOf("M19" to "109", "M20" to "108", "M21" to "115")) {
            val lines = listOf(0, 3).flatMap { pass -> listOf(
                PrintingOcrLine(pass, "$number/280 C", .04f, .91f, .28f, .929f),
                PrintingOcrLine(pass, "$set SP", .04f, .94f, .25f, .96f)) }
            val footer = StructuredPrintingEvidence.read(lines, types.keys)
            val result = HashScanAnalysis.Result(null, listOf(top, runnerUp), emptyList(), listOf("Mind Rot"),
                null, null, null, null, "", emptyList(), 0, 0, 0, 0, 0, byteArrayOf(1),
                options = HashScanAnalysis.Options(true, false, rulesScanner = true, rulesAutoAdd = true),
                structuredPrinting = footer, catalogSetTypes = types)
            val automatic = RulesScanReport.automatic(result)
            assertEquals("$set: $types", "STRUCTURED_FOOTER", automatic.reason)
            assertEquals(set, automatic.variant?.set?.code)
            assertEquals(number, automatic.variant?.collectorNumber)
            val saved = org.json.JSONObject(checkNotNull(RulesScanReport.capture(result,
                checkNotNull(automatic.variant).toEditionOption(), "es", automatic = true)).metadataJson)
            assertEquals("AUTO_STRUCTURED_FOOTER", saved.getString("selectionProvenance"))
        }
    }

    @Test fun modernFooterOcrRetainsWordsAndCorroboratesSeparateCropFamilies() {
        val bitmap = Bitmap.createBitmap(630, 880, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 22f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("123/269 C", 25f, 824f, paint)
        canvas.drawText("M15 EN Alex Stone", 25f, 856f, paint)
        val reader = PrintingLineOcr()
        try {
            val done = CountDownLatch(1)
            var result: StructuredPrintingRead? = null
            var failure: Throwable? = null
            reader.recognize(bitmap, retainSpatialLines = true) { actual, error ->
                failure = error
                result = StructuredPrintingEvidence.read(actual?.spatialLines.orEmpty(), setOf("M15"))
                actual?.preview?.recycle()
                done.countDown()
            }
            assertTrue(done.await(45, TimeUnit.SECONDS))
            assertNull(failure)
            val parsed = checkNotNull(result)
            assertEquals(parsed.lines.toString(), PrintedFooter("M15", "123", "en"), parsed.footer)
            assertTrue(parsed.completeTuplePasses.toString(), parsed.corroboratedTuple)
            assertTrue(parsed.lines.any { it.elements.isNotEmpty() })
        } finally { reader.close(); bitmap.recycle() }
    }

    private var previousAutoAdd: Boolean? = null
    @Before fun disableLiveCollectionWritesDuringUiTests() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("rules_scanner", 0)
        previousAutoAdd = if (prefs.contains("auto_unique_art")) prefs.getBoolean("auto_unique_art", true) else null
        prefs.edit().putBoolean("auto_unique_art", false).commit()
    }
    @After fun restoreAutoAddPreference() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("rules_scanner", 0)
        val editor = prefs.edit()
        previousAutoAdd?.let { editor.putBoolean("auto_unique_art", it) } ?: editor.remove("auto_unique_art")
        editor.commit()
    }

    @Test fun realSoaAndSpgCapturesResolveByArtworkWithoutWritingCollection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val samples = listOf("giant_growth_soa.jpg" to "SOA", "archmage_spg.jpg" to "SPG")
        val directory = java.io.File(context.noBackupFilesDir, "rules_replay")
        assumeTrue("Private rules corpus missing", samples.all { java.io.File(directory, it.first).isFile })
        assertTrue(OpenCVLoader.initLocal())
        val analysis = HashScanAnalysis(context)
        try {
            val ready = CountDownLatch(1)
            var prepared = false
            analysis.prepare(true) { prepared = it; ready.countDown() }
            assertTrue(ready.await(90, TimeUnit.SECONDS))
            assertTrue(prepared)
            samples.forEach { (file, set) ->
                val frame = checkNotNull(BitmapFactory.decodeFile(java.io.File(directory, file).path))
                var result: HashScanAnalysis.Result? = null
                val done = CountDownLatch(1)
                analysis.analyze(frame, HashScanAnalysis.Options(ocr = true, symbol = false, language = true,
                    captureEvidence = true, rulesScanner = true, rulesAutoAdd = true)) { result = it; done.countDown() }
                assertTrue(done.await(60, TimeUnit.SECONDS))
                val actual = checkNotNull(result)
                val automatic = RulesScanReport.automatic(actual)
                assertTrue("$file: ${automatic.reason}; ${actual.errors}", automatic.canAutoAdd)
                assertEquals(set, automatic.variant?.set?.code)
                assertEquals("es", automatic.variant?.languageCode)
                assertEquals("nonfoil", automatic.finish)
                assertTrue(frame.isRecycled)
                assertFalse(RulesScanReport.automatic(actual.copy(options = actual.options.copy(rulesAutoAdd = false))).canAutoAdd)
                val metadata = org.json.JSONObject(checkNotNull(RulesScanReport.capture(actual,
                    checkNotNull(automatic.variant).toEditionOption(), "es", automatic = true)).metadataJson)
                assertEquals("AUTO_CATALOG_UNIQUE_ARTWORK", metadata.getString("selectionProvenance"))
                assertEquals("SCANNER_PREFERENCE", metadata.getString("finishProvenance"))
            }
        } finally { analysis.close() }
    }

    @Test fun evidenceTableShowsReadConflictAndMissingWithoutFixedHelp() {
        val lines = listOf(
            PrintingOcrLine(0, "©1993-2007 Wizards 60/180", .1f, .96f, .9f, .985f),
            PrintingOcrLine(0, "David Day", .75f, .93f, .91f, .95f),
            PrintingOcrLine(3, "©2007 Wizards 60/181", .1f, .96f, .9f, .985f))
        val variant = ArtPrintingIndex.Variant("fixture", "00000000-0000-0000-0000-000000000001",
            "Spin into Myth", "Spin into Myth", "60", ArtPrintingIndex.SetInfo("FUT", "Future Sight", "2007-05-04", "", null, null),
            "en", CardBorderColor.BLACK, 3, "uncommon", 255, null, null)
        val strong = HashScanAnalysis.Row(ArtHashMatcher.Candidate(ArtHashIndex.Hit("test", "art1",
            "Spin into Myth", "FUT", "60", 4, 8), "test"), emptyList(), listOf(variant))
        val other = HashScanAnalysis.Row(ArtHashMatcher.Candidate(ArtHashIndex.Hit("other", "art2",
            "Other", "XXX", "1", 14, 25), "test"), emptyList())
        val result = HashScanAnalysis.Result(null, listOf(strong, other), emptyList(), listOf("Spin into Myth"), null, null, null,
            null, "", emptyList(), 0, 0, 0, 0, 0, byteArrayOf(1),
            structuredPrinting = StructuredPrintingEvidence.read(lines, emptySet()),
            artistReferences = mapOf(PrintingArtistIndex.key("fixture", "art1") to "David Day"), artistReferenceRevision = "fixture")
        ActivityScenario.launch(RulesScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
                RulesScanReview(activity, result, container) { _, _ -> fail("Table cannot save") }.show()
                fun texts(view: View): List<String> = when (view) {
                    is TextView -> listOf(view.text.toString())
                    is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                    else -> emptyList()
                }
                val all = texts(container)
                assertFalse(all.contains(activity.getString(R.string.rules_scan_help)))
                assertTrue(all.contains("1993–2007 / 2007"))
                assertTrue(all.contains("David Day"))
                assertTrue(all.contains("60"))
                assertTrue(all.contains("180 / 181"))
                assertTrue(all.contains(activity.getString(R.string.scan_debug_table_printed_total)))
                val fields = RulesScanReport.json(result).getJSONObject("historicalFooter")
                assertEquals("READ", fields.getJSONObject("collectorNumbers").getString("state"))
                assertEquals("CONFLICT", fields.getJSONObject("printedTotals").getString("state"))
                assertEquals("CONFLICT", fields.getJSONObject("fractions").getString("state"))
                assertTrue(all.contains(activity.getString(R.string.scan_debug_historical_compare_title)))
                assertTrue(all.any { it.contains("Future Sight") })
                val historical = RulesScanReport.json(result).getJSONObject("historicalComparison")
                assertFalse(historical.getBoolean("canAutoAdd"))
                assertEquals("EXACT_PRINTING_AND_ARTWORK", historical.getString("artistComparison"))
                assertTrue(all.contains(activity.getString(R.string.scan_debug_table_art_strong)))
                val unresolved = LinearLayout(activity)
                RulesScanEvidenceTable.addTo(unresolved, result.copy(names = emptyList(), rawTitle = listOf("Farrclite Prist")))
                assertTrue(texts(unresolved).contains(activity.getString(R.string.scan_debug_table_unresolved)))
                assertTrue(texts(unresolved).contains("Farrclite Prist"))
                assertTrue(all.contains(activity.getString(R.string.scan_debug_table_read)))
                assertTrue(all.contains(activity.getString(R.string.scan_debug_table_conflict)))
                assertTrue(all.contains(activity.getString(R.string.scan_debug_table_unreadable)))
                val width = (320 * activity.resources.displayMetrics.density).toInt()
                container.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                container.layout(0, 0, width, container.measuredHeight)
                val bitmap = Bitmap.createBitmap(width, container.height, Bitmap.Config.ARGB_8888)
                container.draw(android.graphics.Canvas(bitmap))
                java.io.File(activity.noBackupFilesDir, "rules_table_preview.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                RulesScanEvidenceTable.addTo(LinearLayout(activity), result.copy(structuredPrinting = StructuredPrintingRead(ScanReadState.UNREADABLE)))
            }
        }
    }

    @Test fun reviewNeedsExplicitConfirmationAndRejectsDuplicateOrStaleSubmit() {
        val result = HashScanAnalysis.Result(null, emptyList(), emptyList(), emptyList(), null, null, null,
            null, "en", emptyList(), 0, 0, 0, 0, 0, byteArrayOf(1))
        val option = CardEditionOption("one", "Card", "Card", "WOE", "Set", "123", "2023", "rare",
            "etched", true, null, "", "", null, null, null, null)
        val selected = mutableListOf<String>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val confirm = RulesScanReview::class.java.getDeclaredMethod("confirm", CardEditionOption::class.java, String::class.java)
            .apply { isAccessible = true }
        val dialogs = RulesScanReview::class.java.getDeclaredField("dialogs").apply { isAccessible = true }
        fun open(review: RulesScanReview): AlertDialog {
            confirm.invoke(review, option, "pt")
            return (dialogs.get(review) as List<*>).last() as AlertDialog
        }
        lateinit var review: RulesScanReview
        ActivityScenario.launch(RulesScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                review = RulesScanReview(activity, result, LinearLayout(activity)) { card, language ->
                    selected += "${card.printingUuid}:${card.finish}:$language"
                }.also { it.show() }
                assertTrue(selected.isEmpty())
                open(review).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            // AlertDialog dispatches its button callbacks via the main Handler, not inside performClick.
            instrumentation.waitForIdleSync()
            assertTrue(selected.isEmpty())
            scenario.onActivity {
                val accepted = open(review).getButton(AlertDialog.BUTTON_POSITIVE)
                accepted.performClick()
                accepted.performClick()
            }
            instrumentation.waitForIdleSync()
            assertEquals(listOf("one:etched:pt"), selected)
            scenario.onActivity { activity ->
                review.close()
                val stale = RulesScanReview(activity, result, LinearLayout(activity)) { _, _ -> selected += "STALE" }
                    .also { it.show() }
                val oldButton = open(stale).getButton(AlertDialog.BUTTON_POSITIVE)
                stale.close()
                oldButton.performClick()
            }
            instrumentation.waitForIdleSync()
            assertEquals(1, selected.size)
            val metadata = org.json.JSONObject(checkNotNull(RulesScanReport.capture(result, option, "pt")).metadataJson)
            assertEquals("USER_CONFIRMED", metadata.getString("selectionProvenance"))
            assertEquals("pt", metadata.getString("effectiveLanguage"))
            assertEquals("", metadata.getJSONObject("rulesScanner").getString("observedLanguage"))
            assertEquals(result.scanId, metadata.getJSONObject("rulesScanner").getString("scanId"))
        }
    }

    @Test fun separateActivityDoesNotReadOrModifyHashPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = context.getSharedPreferences("hash_scanner", 0).all.toMap()
        ActivityScenario.launch(RulesScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.rules_scan_title), activity.findViewById<TextView>(R.id.rapidScanTitle).text)
                assertTrue(activity.findViewById<CheckBox>(R.id.hashScanOcr).isChecked)
                assertTrue(activity.findViewById<CheckBox>(R.id.hashScanLanguage).isChecked)
                assertEquals(context.getSharedPreferences("rules_scanner", 0).getBoolean("auto_unique_art", true),
                    activity.findViewById<CheckBox>(R.id.hashScanAutoAdd).isChecked)
                assertTrue(activity.findViewById<CheckBox>(R.id.hashScanAutoAdd).isShown)
                assertFalse(activity.findViewById<CheckBox>(R.id.hashScanOcr).isShown)
            }
        }
        assertEquals(before, context.getSharedPreferences("hash_scanner", 0).all)
    }

    @Test fun fabFits320dpAndActivityIsNotExported() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val info = context.packageManager.getActivityInfo(ComponentName(context, RulesScanActivity::class.java), 0)
        assertFalse(info.exported)
        instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.Theme_Mtg)
            val root = LayoutInflater.from(themed).inflate(R.layout.ocr_capture, null)
            val density = context.resources.displayMetrics.density
            val width = (320 * density).toInt()
            val height = (640 * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val rules = root.findViewById<View>(R.id.fabRulesScanner)
            val hash = root.findViewById<View>(R.id.fabHashOnlyScanner)
            assertTrue("Rules FAB outside narrow screen", rules.left >= 0 && rules.right <= width)
            assertTrue("FABs overlap", rules.right <= hash.left)
            assertEquals(context.getString(R.string.rules_scan_title), rules.contentDescription)
        }
    }

    @Test fun blankImageReturnsTraceAndNeverAnAutomaticPrinting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(OpenCVLoader.initLocal())
        val analysis = HashScanAnalysis(context)
        try {
            val ready = CountDownLatch(1)
            var prepared = false
            analysis.prepare(true) { prepared = it; ready.countDown() }
            assertTrue(ready.await(90, TimeUnit.SECONDS))
            assertTrue(prepared)
            val bitmap = Bitmap.createBitmap(630, 880, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
            val done = CountDownLatch(1)
            var result: HashScanAnalysis.Result? = null
            analysis.analyze(bitmap, HashScanAnalysis.Options(ocr = true, symbol = false,
                language = true, captureEvidence = true, rulesScanner = true)) { result = it; done.countDown() }
            assertTrue(done.await(60, TimeUnit.SECONDS))
            val actual = checkNotNull(result)
            assertTrue(actual.errors.toString(), actual.errors.isEmpty())
            assertTrue(bitmap.isRecycled)
            assertNotNull(actual.capturedJpeg)
            assertEquals(ScanReadState.UNREADABLE, actual.structuredPrinting.state)
            val decision = RulesScanReport.decision(actual)
            assertEquals("", decision.language)
            assertFalse(RulesScanReport.automatic(actual).canAutoAdd)
            assertEquals("ART_INDEX_ONLY", RulesScanReport.json(actual).getString("coverage"))
            val saved = java.io.File(context.filesDir, "rules_scan").listFiles().orEmpty()
                .firstOrNull { it.name.endsWith(actual.scanId) }
            assertNotNull(saved)
            assertTrue(java.io.File(saved, "metadata.json").isFile)
        } finally { analysis.close() }
    }
    @Test fun originalOcrControlLivesInScannerAndRulesHasZoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_Mtg)).inflate(R.layout.ocr_capture, null)
            val control = root.findViewById<View>(R.id.checkEnhancedOcr)
            assertEquals(R.id.scanActions, (control.parent as View).id)
        }
        ActivityScenario.launch(RulesScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(1000, activity.findViewById<android.widget.SeekBar>(R.id.scanZoom).max)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanZoom).visibility)
            }
        }
    }
    @Test fun zoomSurvivesCameraRebindAndRetryBudgetOnlySurvivesAutomaticReturn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("camera_zoom", android.content.Context.MODE_PRIVATE)
        val key = "RulesScanActivity_rear_ratio"
        val hadValue = prefs.contains(key)
        val oldValue = prefs.getFloat(key, 1f)
        prefs.edit().putFloat(key, 1.7f).commit()
        fun freeze(activity: RulesScanActivity) {
            val field = RapidEditionScanActivity::class.java.getDeclaredField("captureGate").apply { isAccessible = true }
            (field.get(activity) as java.util.concurrent.atomic.AtomicBoolean).set(true)
        }
        try {
            ActivityScenario.launch(RulesScanActivity::class.java).use { scenario ->
                scenario.onActivity(::freeze)
                var lastBinding = 0
                fun awaitZoom() {
                    var restored = false
                    var details = ""
                    val deadline = android.os.SystemClock.elapsedRealtime() + 15000
                    while (!restored && android.os.SystemClock.elapsedRealtime() < deadline) {
                        scenario.onActivity { activity ->
                            freeze(activity)
                            val field = RapidEditionScanActivity::class.java.getDeclaredField("zoomCamera").apply { isAccessible = true }
                            val camera = field.get(activity) as? androidx.camera.core.Camera
                            val state = camera?.cameraInfo?.zoomState?.value
                            val bindingField = RapidEditionScanActivity::class.java.getDeclaredField("zoomBindingGeneration").apply { isAccessible = true }
                            val binding = bindingField.getInt(activity)
                            val restoringField = RapidEditionScanActivity::class.java.getDeclaredField("zoomRestoreInFlight").apply { isAccessible = true }
                            restored = !restoringField.getBoolean(activity) && binding > lastBinding && state != null && kotlin.math.abs(state.zoomRatio -
                                ScannerZoomPolicy.clamp(1.7f, state.minZoomRatio, state.maxZoomRatio)) < .03f
                            details = "binding=$binding previous=$lastBinding zoom=${state?.zoomRatio} camera=${camera?.cameraInfo?.cameraState?.value} " +
                                listOf("analysisInFlight", "cameraStarting", "hashLiveResultMode", "correctionMode", "zoomRestoreInFlight").joinToString { name ->
                                    val f = RapidEditionScanActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
                                    "$name=${f.get(activity)}"
                                }
                            if (restored) lastBinding = binding
                        }
                        if (!restored) Thread.sleep(100)
                    }
                    assertTrue("Actual CameraX zoom was not restored: $details", restored)
                    assertEquals(1.7f, prefs.getFloat(key, 0f), .001f)
                }
                awaitZoom()
                scenario.onActivity { activity ->
                    val retryField = RapidEditionScanActivity::class.java.getDeclaredField("rulesRetry").apply { isAccessible = true }
                    val policy = retryField.get(activity) as RulesRetryPolicy
                    policy.beginAttempt()
                    val method = RapidEditionScanActivity::class.java.getDeclaredMethod("returnToCamera",
                        Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                    method.invoke(activity, false, true)
                    freeze(activity)
                    assertEquals(1, policy.attempts)
                }
                awaitZoom()
                scenario.onActivity { activity ->
                    val method = RapidEditionScanActivity::class.java.getDeclaredMethod("returnToCamera",
                        Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                    method.invoke(activity, false, false)
                    freeze(activity)
                    val field = RapidEditionScanActivity::class.java.getDeclaredField("rulesRetry").apply { isAccessible = true }
                    assertEquals(0, (field.get(activity) as RulesRetryPolicy).attempts)
                }
                awaitZoom()
                scenario.recreate()
                lastBinding = 0
                scenario.onActivity(::freeze)
                awaitZoom()
            }
        } finally {
            if (hadValue) prefs.edit().putFloat(key, oldValue).commit() else prefs.edit().remove(key).commit()
        }
    }
}
