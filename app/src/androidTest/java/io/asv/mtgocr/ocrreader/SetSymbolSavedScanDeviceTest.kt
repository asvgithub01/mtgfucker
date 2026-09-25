package io.asv.mtgocr.ocrreader

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

/** Private, opt-in replay corpus in no_backup/symbol_v2_replay; never bundles user photos in the APK. */
class SetSymbolSavedScanDeviceTest {
    @Test fun tempestAndFutureFrameResolveWithRealCaptureOptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "symbol_v25_replay")
        val manifest = File(directory, "manifest.json")
        assumeTrue("Private Tempest/Future corpus not installed", manifest.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val samples = JSONObject(manifest.readText()).getJSONArray("samples")
        val analysis = HashScanAnalysis(context)
        val results = org.json.JSONArray()
        val failures = mutableListOf<String>()
        try {
            val prepared = CountDownLatch(1)
            var ready = false
            analysis.prepare(true) { ready = it; prepared.countDown() }
            assertTrue(prepared.await(60, TimeUnit.SECONDS)); assertTrue(ready)
            for (i in 0 until samples.length()) {
                val sample = samples.getJSONObject(i)
                val file = sample.getString("file")
                require(!file.contains('/') && !file.contains('\\'))
                val bitmap = checkNotNull(BitmapFactory.decodeFile(File(directory, file).path))
                val latch = CountDownLatch(1)
                var result: HashScanAnalysis.Result? = null
                analysis.analyze(bitmap, HashScanAnalysis.Options(ocr = sample.optBoolean("ocr"), symbol = false, symbolV2 = true)) {
                    result = it; latch.countDown()
                }
                assertTrue(latch.await(30, TimeUnit.SECONDS))
                val actual = checkNotNull(result)
                val resolved = actual.rows.firstOrNull { it.resolvedVariant != null }
                results.put(JSONObject().put("file", file).put("symbol", actual.symbolV2?.json())
                    .put("names", org.json.JSONArray(actual.names)).put("title", org.json.JSONArray(actual.rawTitle))
                    .put("topArt", actual.rows.firstOrNull()?.candidate.toString())
                    .put("resolved", resolved?.resolvedVariant?.printingUuid)
                    .put("illustrationId", resolved?.candidate?.hit?.illustrationId))
                File(directory, "results.json").writeText(results.toString(2))
                assertTrue(actual.errors.toString(), actual.errors.isEmpty())
                val expected = sample.optString("expectedSet")
                if (expected.isNotEmpty()) {
                    if (actual.symbolV2?.selectedSet != expected) failures += "$file expected=$expected ${actual.symbolV2?.json()}"
                    if (resolved?.resolvedVariant?.set?.code != expected || resolved?.candidate?.hit?.name != sample.getString("expectedName"))
                        failures += "$file no correct resolved printing: $resolved"
                    if (resolved != null && !HashAutoAddPolicy.acceptsTopHit(resolved.candidate.hit.phashDistance,
                        resolved.resolvedEdition != null, resolved.resolvedVariant != null)) failures += "$file not auto-add eligible"
                } else if (actual.symbolV2?.selectedSet != null && actual.symbolV2.selectedSet != sample.optString("allowedSet"))
                    failures += "$file incorrect control confirmation"
                assertTrue(bitmap.isRecycled)
            }
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        } finally { analysis.close() }
    }

    @Test fun confirmedM13IsNotVetoedByRulesTextInFooterOcr() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "symbol_v24_ocr")
        val manifest = File(directory, "manifest.json")
        assumeTrue("Private OCR corpus not installed", manifest.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val samples = JSONObject(manifest.readText()).getJSONArray("samples")
        val analysis = HashScanAnalysis(context)
        val results = org.json.JSONArray()
        try {
            val prepared = CountDownLatch(1)
            var ready = false
            analysis.prepare(true) { ready = it; prepared.countDown() }
            assertTrue(prepared.await(60, TimeUnit.SECONDS)); assertTrue(ready)
            for (i in 0 until samples.length()) {
                val sample = samples.getJSONObject(i)
                val file = sample.getString("file")
                require(!file.contains('/') && !file.contains('\\'))
                val bitmap = checkNotNull(BitmapFactory.decodeFile(File(directory, file).path))
                val latch = CountDownLatch(1)
                var result: HashScanAnalysis.Result? = null
                analysis.analyze(bitmap, HashScanAnalysis.Options(ocr = true, symbol = false, symbolV2 = true)) {
                    result = it; latch.countDown()
                }
                assertTrue(latch.await(30, TimeUnit.SECONDS))
                val actual = checkNotNull(result)
                results.put(JSONObject().put("file", file).put("symbol", actual.symbolV2?.json())
                    .put("printing", actual.printing.toString()).put("language", actual.effectiveLanguage)
                    .put("resolved", actual.rows.firstOrNull()?.resolvedVariant?.printingUuid))
                File(directory, "results.json").writeText(results.toString(2))
                assertTrue(actual.errors.toString(), actual.errors.isEmpty())
                assertEquals(file, sample.getString("expectedSet"), actual.symbolV2?.selectedSet)
                val row = checkNotNull(actual.rows.firstOrNull())
                assertNotNull("$file ${actual.printing} ${row.compatibleVariants}", row.resolvedVariant)
                assertEquals(sample.getString("expectedSet"), row.resolvedVariant?.set?.code)
                assertTrue(HashAutoAddPolicy.acceptsTopHit(row.candidate.hit.phashDistance,
                    row.resolvedEdition != null, row.resolvedVariant != null))
                assertTrue(bitmap.isRecycled)
            }
        } finally { analysis.close() }
    }

    @Test fun positiveSavedPhotosResolveInFullHashPipelineWithUsersChecks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "symbol_v2_replay")
        val manifest = File(directory, "manifest.json")
        assumeTrue("Private replay corpus not installed", manifest.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val samples = JSONObject(manifest.readText()).getJSONArray("samples")
        // Exercise final UUID and auto-add eligibility, not just the symbol matcher.
        val latestDirectory = File(context.noBackupFilesDir, "symbol_v2_latest")
        val latestManifest = File(latestDirectory, "manifest.json")
        if (latestManifest.isFile) {
            val latest = JSONObject(latestManifest.readText()).getJSONArray("samples")
            for (i in 0 until latest.length()) samples.put(latest.getJSONObject(i).put("latest", true))
        }
        val rarityDirectory = File(context.noBackupFilesDir, "symbol_v23_replay")
        val rarityManifest = File(rarityDirectory, "manifest.json")
        if (rarityManifest.isFile) {
            val rarity = JSONObject(rarityManifest.readText()).getJSONArray("samples")
            for (i in 0 until rarity.length()) samples.put(rarity.getJSONObject(i).put("rarity", true))
        }
        val analysis = HashScanAnalysis(context)
        try {
            val prepared = CountDownLatch(1)
            var ready = false
            analysis.prepare(false) { ready = it; prepared.countDown() }
            assertTrue(prepared.await(30, TimeUnit.SECONDS))
            assertTrue(ready)
            for (i in 0 until samples.length()) {
                val sample = samples.getJSONObject(i)
                val expected = sample.optString("expectedSet").takeIf { it.isNotBlank() } ?: continue
                val name = sample.getString("file")
                require(!name.contains('/') && !name.contains('\\'))
                val sampleDirectory = when { sample.optBoolean("rarity") -> rarityDirectory
                    sample.optBoolean("latest") -> latestDirectory
                    else -> directory }
                val card = checkNotNull(BitmapFactory.decodeFile(File(sampleDirectory, name).path))
                val complete = CountDownLatch(1)
                var result: HashScanAnalysis.Result? = null
                analysis.analyze(card, HashScanAnalysis.Options(ocr = false, symbol = false, symbolV2 = true)) {
                    result = it; complete.countDown()
                }
                assertTrue(complete.await(30, TimeUnit.SECONDS))
                val actual = checkNotNull(result)
                assertTrue(actual.errors.toString(), actual.errors.isEmpty())
                assertEquals("$name ${actual.symbolV2?.json()}", expected, actual.symbolV2?.selectedSet)
                val row = checkNotNull(actual.rows.firstOrNull())
                assertEquals(sample.getString("expectedUuid"), row.resolvedVariant?.printingUuid)
                assertTrue(HashAutoAddPolicy.acceptsTopHit(row.candidate.hit.phashDistance,
                    row.resolvedEdition != null, row.resolvedVariant != null))
                assertTrue(card.isRecycled)
            }
        } finally { analysis.close() }
    }

    /** Diagnostic replay: independent of collection and live camera; results stay in private no-backup storage. */
    @Test fun replaysLatestFramesAndPreservesAbstentions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "symbol_v2_latest")
        val manifest = File(directory, "manifest.json")
        assumeTrue("Private latest corpus not installed", manifest.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val index = ArtPrintingIndex.read(context.assets.open(ArtPrintingIndex.ASSET))
        val artMatcher = ArtHashMatcher(ArtHashIndex.read(context.assets.open(ArtHashIndex.ASSET)))
        val references = SetSymbolShapeMatcher(context, OkHttpClient())
        val matcher = SetSymbolHashMatcher(references::referenceMask)
        val samples = JSONObject(manifest.readText()).getJSONArray("samples")
        val results = org.json.JSONArray()
        val failures = ArrayList<String>()
        try {
            for (i in 0 until samples.length()) {
                val sample = samples.getJSONObject(i)
                val name = sample.getString("file")
                require(!name.contains('/') && !name.contains('\\'))
                val card = checkNotNull(BitmapFactory.decodeFile(File(directory, name).path))
                try {
                    val art = artMatcher.match(card).candidates.first()
                    val variants = index.variants(art.hit.illustrationId)
                    val result = matcher.match(card, variants.map { it.set.code }, SetSymbolHashPolicy.retainedSymbols(variants))
                    results.put(result.json().put("file", name).put("cardName", art.hit.name).put("phash", art.hit.phashDistance).put("illustrationId", art.hit.illustrationId).put("variants", org.json.JSONArray().also { list ->
                        variants.distinctBy { it.printingUuid }.forEach { list.put(JSONObject().put("set", it.set.code).put("uuid", it.printingUuid)) }
                    }))
                    val expected = sample.optString("expectedSet").takeIf { it.isNotBlank() }
                    if ((expected != null && expected != result.selectedSet) ||
                        (expected == null && result.selectedSet != null && sample.optString("allowedSet") != result.selectedSet)) failures += "$name expected=$expected actual=${result.json()}"
                    if (expected != null) {
                        if (sample.getString("illustrationId") != art.hit.illustrationId) failures += "$name wrong artwork: ${art.hit}"
                        if (sample.getString("expectedUuid") != SetSymbolHashPolicy.selectVariant(variants, result.selectedSet)?.printingUuid)
                            failures += "$name wrong UUID"
                    }
                } finally { card.recycle() }
            }
            File(directory, "results.json").writeText(results.toString())
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        } finally { matcher.close(); references.close() }
    }

    /** Diagnostic replay: independent of collection and live camera; results stay in private no-backup storage. */
    @Test fun replaysRarityAndWideSymbolsFromPrivateCorpus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "symbol_v23_replay")
        val manifest = File(directory, "manifest.json")
        assumeTrue("Private latest corpus not installed", manifest.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val index = ArtPrintingIndex.read(context.assets.open(ArtPrintingIndex.ASSET))
        val artMatcher = ArtHashMatcher(ArtHashIndex.read(context.assets.open(ArtHashIndex.ASSET)))
        val references = SetSymbolShapeMatcher(context, OkHttpClient())
        val matcher = SetSymbolHashMatcher(references::referenceMask)
        val samples = JSONObject(manifest.readText()).getJSONArray("samples")
        val results = org.json.JSONArray()
        val failures = ArrayList<String>()
        try {
            for (i in 0 until samples.length()) {
                val sample = samples.getJSONObject(i)
                val name = sample.getString("file")
                require(!name.contains('/') && !name.contains('\\'))
                val card = checkNotNull(BitmapFactory.decodeFile(File(directory, name).path))
                try {
                    val art = artMatcher.match(card).candidates.first()
                    val variants = index.variants(art.hit.illustrationId)
                    val result = matcher.match(card, variants.map { it.set.code }, SetSymbolHashPolicy.retainedSymbols(variants))
                    results.put(result.json().put("file", name).put("cardName", art.hit.name).put("phash", art.hit.phashDistance).put("illustrationId", art.hit.illustrationId).put("variants", org.json.JSONArray().also { list ->
                        variants.distinctBy { it.printingUuid }.forEach { list.put(JSONObject().put("set", it.set.code).put("uuid", it.printingUuid)) }
                    }))
                    val expected = sample.optString("expectedSet").takeIf { it.isNotBlank() }
                    if ((expected != null && expected != result.selectedSet) ||
                        (expected == null && result.selectedSet != null && sample.optString("allowedSet") != result.selectedSet)) failures += "$name expected=$expected actual=${result.json()}"
                    if (expected != null) {
                        if (sample.getString("illustrationId") != art.hit.illustrationId) failures += "$name wrong artwork: ${art.hit}"
                        if (sample.getString("expectedUuid") != SetSymbolHashPolicy.selectVariant(variants, result.selectedSet)?.printingUuid)
                            failures += "$name wrong UUID"
                    }
                } finally { card.recycle() }
            }
            File(directory, "results.json").writeText(results.toString())
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        } finally { matcher.close(); references.close() }
    }

    @Test fun replaysSavedPhotosAgainstAllCandidateSetsAndResolvesExpectedUuid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "symbol_v2_replay")
        val manifest = File(directory, "manifest.json")
        assumeTrue("Private replay corpus not installed", manifest.isFile)
        assertTrue(OpenCVLoader.initLocal())
        val index = ArtPrintingIndex.read(context.assets.open(ArtPrintingIndex.ASSET))
        val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
        val references = SetSymbolShapeMatcher(context, client)
        val matcher = SetSymbolHashMatcher(references::referenceMask)
        val samples = JSONObject(manifest.readText()).getJSONArray("samples")
        try {
            for (i in 0 until samples.length()) {
                val sample = samples.getJSONObject(i)
                val name = sample.getString("file")
                require(!name.contains('/') && !name.contains('\\'))
                val card = checkNotNull(BitmapFactory.decodeFile(File(directory, name).path))
                try {
                    val variants = index.variants(sample.getString("illustrationId"))
                    val result = matcher.match(card, variants.map { it.set.code }, SetSymbolHashPolicy.retainedSymbols(variants))
                    Log.i("SymbolReplay", "$name ${result.json()}")
                    val expected = sample.optString("expectedSet").takeIf { it.isNotBlank() }
                    assertEquals("$name ${result.json()}", expected, result.selectedSet)
                    if (expected != null) assertEquals(sample.getString("expectedUuid"),
                        SetSymbolHashPolicy.selectVariant(variants, result.selectedSet)?.printingUuid)
                } finally { card.recycle() }
            }
        } finally { matcher.close(); references.close(); client.dispatcher.cancelAll() }
    }
}
