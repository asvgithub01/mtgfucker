package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.MtgJsonCatalogDataProvider
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Owns each rectified bitmap until every enabled analysis has completed. No collection writes. */
internal class HashScanAnalysis(context: Context) {
    data class Options(
        val ocr: Boolean,
        val symbol: Boolean,
        val border: Boolean = false,
        val language: Boolean = false,
        val captureEvidence: Boolean = false,
        val symbolV2: Boolean = false,
        val symbolRetryAttempt: Int = 0,
        val editionPicker: Boolean = false,
        val probableEdition: Boolean = false,
        val rulesScanner: Boolean = false,
        val rulesAutoAdd: Boolean = false,
        val rulesPreferredFinish: String = "nonfoil",
        val rulesAttempt: Int = 0,
        val boundaryOriginal: ByteArray? = null,
        val boundaryQuad: String = "",
        val boundaryMode: String = ""
    )
    data class Edition(
        val code: String,
        val name: String,
        val year: Int?,
        val collectorNumber: String,
        val printingUuid: String
    )
    data class Row(
        val candidate: ArtHashMatcher.Candidate,
        val editions: List<Edition>,
        val variants: List<ArtPrintingIndex.Variant> = emptyList(),
        val resolvedEdition: Edition? = null,
        val compatibleVariants: List<ArtPrintingIndex.Variant> = emptyList(),
        val resolvedVariant: ArtPrintingIndex.Variant? = null,
        val resolvedByUniqueArtwork: Boolean = false,
        val probableVariant: ArtPrintingIndex.Variant? = null,
        val retainedVariant: ArtPrintingIndex.Variant? = null
    )
    data class Result(
        val hash: ArtHashMatcher.Result?, val rows: List<Row>, val rawTitle: List<String>,
        val names: List<String>, val printing: PrintingMetadataGuess?,
        val symbols: SetSymbolShapeMatch?, val border: CardFrameAnalysis?,
        val language: CardTextLanguageResult?, val effectiveLanguage: String,
        val errors: List<String>, val elapsedMs: Long, val ocrMs: Long,
        val symbolMs: Long, val borderMs: Long, val languageMs: Long,
        val capturedJpeg: ByteArray?,
        val symbolV2: SetSymbolHashMatch? = null,
        val options: Options = Options(false, false),
        val manualSetCode: String? = null,
        val structuredPrinting: StructuredPrintingRead = StructuredPrintingRead(ScanReadState.UNREADABLE),
        val scanId: String = java.util.UUID.randomUUID().toString(),
        val catalogSetTypes: Map<String, String> = emptyMap(),
        val captureQuality: Map<String, Double> = emptyMap(),
        val artistReferences: Map<String, String> = emptyMap(),
        val artistReferenceRevision: String = "",
        val titleLanguage: TitleLanguageIndex.Evidence = TitleLanguageIndex.Evidence()
    )

    private val app = context.applicationContext
    private val repository by lazy { CardRepository.get(app) }
    private val workers = Executors.newFixedThreadPool(3)
    private val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    private val symbols = SetSymbolShapeMatcher(app, client)
    private val symbolHashes = SetSymbolHashMatcher(symbols::referenceMask)
    @Volatile private var matcher: ArtHashMatcher? = null
    @Volatile private var printingIndex: ArtPrintingIndex? = null
    // Optional sidecar: failure leaves artist evidence unassessed, never changes auto-add gates.
    private val artistIndex by lazy {
        runCatching { PrintingArtistIndex.read(app.assets.open(PrintingArtistIndex.ASSET),
            PrintingArtistIndex.sha256(app.assets.open(ArtPrintingIndex.ASSET))) }.getOrNull()
    }
    private val titleLanguages by lazy {
        runCatching { TitleLanguageIndex.read(app.assets.open(TitleLanguageIndex.ASSET)) }.getOrNull()
    }
    @Volatile private var closed = false

    fun prepare(ocr: Boolean, callback: (Boolean) -> Unit) {
        workers.execute {
            val ready = runCatching {
                if (matcher == null) matcher = ArtHashMatcher(ArtHashIndex.read(app.assets.open(ArtHashIndex.ASSET)))
                if (printingIndex == null) {
                    printingIndex = ArtPrintingIndex.read(app.assets.open(ArtPrintingIndex.ASSET))
                }
            }.isSuccess
            if (closed) return@execute
            if (ocr && ready) {
                try { repository.prepareCardNamePredictor { callback(it && !closed) } }
                catch (_: Exception) { callback(false) }
            } else callback(ready)
        }
    }

    fun analyze(
        card: Bitmap,
        options: Options,
        onHashReady: (ArtHashMatcher.Result) -> Unit = {},
        callback: (Result) -> Unit
    ) = analyzePrepared(card, if (options.editionPicker) options.copy(
        ocr = true, symbol = false, captureEvidence = true, symbolRetryAttempt = 0
    ) else if (options.probableEdition) options.copy(captureEvidence = true) else options, onHashReady, callback)

    private fun analyzePrepared(
        card: Bitmap,
        options: Options,
        onHashReady: (ArtHashMatcher.Result) -> Unit,
        callback: (Result) -> Unit
    ) {
        val started = SystemClock.elapsedRealtime()
        val lock = Any()
        var captureQuality = emptyMap<String, Double>()
        var hash: ArtHashMatcher.Result? = null
        var rows = emptyList<Row>()
        var title = emptyList<String>()
        var names = emptyList<String>()
        var printing: PrintingMetadataGuess? = null
        var structuredPrinting = StructuredPrintingRead(ScanReadState.UNREADABLE)
        var catalogSetCodes: Set<String> = emptySet()
        var catalogSetTypes: Map<String, String> = emptyMap()
        var shape: SetSymbolShapeMatch? = null
        var border: CardFrameAnalysis? = null
        var language: CardTextLanguageResult? = null
        var ocrMs = 0L
        var symbolMs = 0L
        var borderMs = 0L
        var languageMs = 0L
        val errors = mutableListOf<String>()
        fun fail(stage: String, error: Throwable) = synchronized(lock) {
            errors.add("$stage: ${error.message ?: error.javaClass.simpleName}")
        }
        val completion = HashScanCompletion(options.ocr, options.language) {
            // The last OCR callback may be on the UI thread. Final matching and disk IO never are.
            try { workers.execute {
                if (closed) { card.recycle(); return@execute }
                if (options.symbolV2 || options.editionPicker || options.probableEdition) printing = HashSymbolPrintingEvidence.trusted(printing, catalogSetCodes)
                val v2CandidateIndex = if (options.editionPicker) HashEditionResolutionPolicy.identityIndex(
                    rows.map { it.candidate.hit.name }, names
                ) else SetSymbolHashPolicy.candidateIndex(
                    rows.map { it.candidate.hit.name }, names, options.ocr
                )
                if (options.editionPicker && v2CandidateIndex != null) {
                    val selected = rows[v2CandidateIndex]
                    // OCR confirms identity, not which of several candidate artworks was printed.
                    // Keep all matching-art alternatives so a symbol cannot select an arbitrary UUID.
                    val variants = HashEditionResolutionPolicy.identityVariants(selected.candidate.hit.name, rows.map { it.variants })
                    val editions = variants.distinctBy { it.printingUuid }.map {
                        Edition(it.set.code, it.set.name, it.set.releaseDate.take(4).toIntOrNull(), it.collectorNumber, it.printingUuid)
                    }
                    rows = rows.mapIndexed { index, row ->
                        if (index == v2CandidateIndex) row.copy(variants = variants, editions = editions) else row
                    }
                }
                val footerResolved = if (options.editionPicker) v2CandidateIndex?.let { index ->
                    HashEditionResolutionPolicy.automatic(rows[index].variants, printing, null,
                        printing?.languageCode ?: "en")
                } else null
                val v2 = if (options.symbolV2 && footerResolved == null && !closed) {
                    val selected = v2CandidateIndex?.let(rows::get)
                    if (selected == null) SetSymbolHashMatch(reason = "identidad_no_confirmada")
                    else runCatching {
                        symbolHashes.match(card, selected.editions.map { it.code },
                            SetSymbolHashPolicy.retainedSymbols(selected.variants),
                            CardLanguageEvidenceResolver.resolve(printing?.languageCode,
                                language?.languageCode, language?.confidence ?: 0f))
                            .copy(cardName = selected.candidate.hit.name)
                    }.getOrElse { error ->
                        fail("símbolo V2", error)
                        SetSymbolHashMatch(reason = "error_comparacion")
                    }
                } else null
                val capturedJpeg = if (options.captureEvidence || options.symbolV2) runCatching {
                    ByteArrayOutputStream().use { output ->
                        check(card.compress(Bitmap.CompressFormat.JPEG, 86, output))
                        output.toByteArray()
                    }
                }.onFailure { fail("foto", it) }.getOrNull() else null
                card.recycle()
                val artistReference = if (options.rulesScanner) artistIndex else null
                val artistReferences = if (artistReference == null) emptyMap() else buildMap {
                    rows.forEach { row -> row.candidate.hit.illustrationId?.let { illustration ->
                        row.variants.distinctBy { it.printingUuid }.forEach { variant ->
                            artistReference.artist(variant.printingUuid, illustration)?.let { artist ->
                                put(PrintingArtistIndex.key(variant.printingUuid, illustration), artist)
                            }
                        }
                    } }
                }
                val titleEvidence = if (options.rulesScanner) titleLanguages?.lookup(title, names)
                    ?: TitleLanguageIndex.Evidence() else TitleLanguageIndex.Evidence()
                val result = synchronized(lock) {
                    val effectiveLanguage = if (options.symbolV2 && !options.ocr) "en" else CardLanguageEvidenceResolver.resolve(
                        footerLanguage = printing?.languageCode,
                        detectedRulesLanguage = language?.languageCode,
                        detectedRulesConfidence = language?.confidence ?: 0f
                    )
                    val resolvedRows = rows.mapIndexed { index, row ->
                        val compatibleBeforeSymbol = HashPrintingVariantPolicy.compatible(
                            row.candidate.hit.name, row.variants, names, printing,
                            border?.borderColor, effectiveLanguage,
                            verifyBorder = options.border,
                            verifyLanguage = options.language || ((options.editionPicker || !options.symbolV2) && printing?.languageCode != null)
                        )
                        val hit = row.candidate.hit
                        val unique = if ((options.symbolV2 || options.editionPicker) && index == v2CandidateIndex &&
                            HashEditionResolutionPolicy.recognizedArtwork(hit.name, names, hit.phashDistance, hit.dhashDistance)) {
                            HashEditionResolutionPolicy.uniqueArtwork(row.variants, compatibleBeforeSymbol, printing,
                                effectiveLanguage.ifBlank { "en" })
                        } else null
                        val compatible = if (options.symbolV2 && !options.editionPicker && unique == null) compatibleBeforeSymbol.filter {
                            index == v2CandidateIndex && v2?.selectedSet?.equals(it.set.code, true) == true
                        } else compatibleBeforeSymbol
                        row.copy(
                            probableVariant = if (options.probableEdition && index == v2CandidateIndex &&
                                HashEditionResolutionPolicy.recognizedArtwork(hit.name, names, hit.phashDistance, hit.dhashDistance)) {
                                HashProbableEditionPolicy.choose(compatibleBeforeSymbol, printing, v2?.selectedSet,
                                    v2?.scores.orEmpty().associate { it.setCode to it.distance },
                                    hit.setCode, hit.collectorNumber, effectiveLanguage.ifBlank { "en" })
                            } else null,
                            resolvedEdition = HashScanEvidence.resolveEdition(
                                row.candidate.hit.name,
                                names,
                                printing,
                                row.editions
                            ),
                            compatibleVariants = compatible,
                            resolvedByUniqueArtwork = unique != null,
                            resolvedVariant = unique ?: if (options.editionPicker) {
                                if (index == v2CandidateIndex) HashEditionResolutionPolicy.automatic(
                                    compatibleBeforeSymbol, printing, v2?.selectedSet, effectiveLanguage.ifBlank { "en" }
                                ) else null
                            } else if (options.symbolV2) SetSymbolHashPolicy.selectVariant(
                                compatible, v2?.selectedSet, effectiveLanguage.ifBlank { "en" }
                            ) else compatible.singleOrNull()
                        )
                    }
                    Result(
                        hash, resolvedRows, title, names, printing, shape, border,
                        language, effectiveLanguage, errors.toList(),
                        SystemClock.elapsedRealtime() - started, ocrMs, symbolMs + (v2?.elapsedMs ?: 0),
                        borderMs, languageMs, capturedJpeg, v2, options,
                        structuredPrinting = if (artistReference == null) structuredPrinting else structuredPrinting.copy(
                            historical = structuredPrinting.historical.copy(
                                artists = artistReference.dictionary.normalize(structuredPrinting.historical.artists))),
                        catalogSetTypes = catalogSetTypes, captureQuality = captureQuality,
                        artistReferences = artistReferences, artistReferenceRevision = artistReference?.sourceSha256.orEmpty(),
                        titleLanguage = titleEvidence
                    )
                }.let { if (options.rulesScanner) it.copy(effectiveLanguage = RulesScanReport.decision(it).language) else it }
                if ((options.symbolV2 || options.editionPicker || options.probableEdition) && !closed) runCatching {
                    SymbolScanDiagnostics.save(app, result)
                }.onFailure { fail("diagnóstico V2", it) }
                if (options.rulesScanner && !closed) runCatching {
                    RulesScanReport.save(app, result)
                }.onFailure { fail("diagnóstico reglas", it) }
                if (!closed) callback(result.copy(errors = synchronized(lock) { errors.toList() }))
            } } catch (_: java.util.concurrent.RejectedExecutionException) {
                card.recycle() // Activity closed while an asynchronous OCR callback was in flight.
            }
        }
        // Hash and both OCR passes start independently. Symbol templates are restricted to the
        // hash candidates' cached printings, then compared while OCR is still running.
        workers.execute {
            if (options.border) {
                val borderStarted = SystemClock.elapsedRealtime()
                runCatching { CardFrameAnalyzer.analyzeTightCard(card) }
                    .onSuccess { analysis -> synchronized(lock) {
                        border = analysis
                        borderMs = SystemClock.elapsedRealtime() - borderStarted
                    } }
                    .onFailure { error ->
                        synchronized(lock) { borderMs = SystemClock.elapsedRealtime() - borderStarted }
                        fail("borde", error)
                    }
            }
            try {
                if (options.rulesScanner) captureQuality = OcrCaptureQuality.measure(card)
                // Keep the hash input size stable; CLAHE belongs exclusively to OCR crops.
                val hashCard = if (options.rulesScanner && card.width != 630)
                    Bitmap.createScaledBitmap(card, 630, 880, true) else card
                val matched = try { checkNotNull(matcher).match(hashCard) }
                    finally { if (hashCard !== card) hashCard.recycle() }
                synchronized(lock) {
                    hash = matched
                    rows = matched.candidates.map { candidate ->
                        Row(candidate, emptyList(), printingIndex!!.variants(candidate.hit.illustrationId))
                    }
                }
                if (!closed) onHashReady(matched)
                val dao = if (options.ocr || options.symbol || options.symbolV2) CardDatabase.get(app).cardDao() else null
                val sets = if (options.ocr || options.symbol || options.symbolV2) dao!!.magicSets().associateBy { it.code.uppercase(Locale.ROOT) }
                    else emptyMap()
                synchronized(lock) {
                    catalogSetCodes = sets.keys
                    if (options.rulesScanner) catalogSetTypes = sets.mapValues { it.value.type }
                }
                val candidateRows = matched.candidates.map { candidate ->
                    val variants = printingIndex!!.variants(candidate.hit.illustrationId)
                    val cachedEditions = variants.distinctBy { it.printingUuid }.map { variant ->
                        Edition(
                            variant.set.code,
                            variant.set.name,
                            variant.set.releaseDate.take(4).toIntOrNull(),
                            variant.collectorNumber,
                            variant.printingUuid
                        )
                    }
                    val printings = if (cachedEditions.isEmpty() && (options.ocr || options.symbol || options.symbolV2)) {
                        dao!!.printingsByName(MtgJsonCatalogDataProvider.normalize(candidate.hit.name))
                    } else emptyList()
                    val fallbackEditions = printings.distinctBy { it.uuid }.map { printing ->
                        val code = printing.setCode.uppercase(Locale.ROOT)
                        Edition(
                            code,
                            sets[code]?.name ?: printing.setName.ifBlank { code },
                            printing.releaseDate.take(4).toIntOrNull()
                                ?: sets[code]?.releaseDate?.take(4)?.toIntOrNull(),
                            printing.collectorNumber,
                            printing.uuid
                        )
                    }
                    Row(candidate, cachedEditions.ifEmpty { fallbackEditions }, variants)
                }
                synchronized(lock) { rows = candidateRows }
                if (options.symbol && !options.symbolV2 && !closed) {
                    val symbolStarted = SystemClock.elapsedRealtime()
                    // match() caps a batch at 48; do not silently omit older sets.
                    val distances = candidateRows.flatMap { it.editions }.map { it.code }.distinct()
                        .chunked(48).flatMap { codes ->
                            if (closed) emptyList() else symbols.match(card, Rect(0, 0, card.width, card.height), codes)
                                .distanceBySetCode.entries.map { it.key to it.value }
                        }.toMap()
                    synchronized(lock) {
                        shape = SetSymbolShapeMatch(distances, distances.size, HashScanEvidence.reliableSymbol(distances.values))
                        symbolMs = SystemClock.elapsedRealtime() - symbolStarted
                    }
                }
            } catch (error: Throwable) { fail("hash/símbolo", error) }
            finally { completion.finish("visual") }
        }
        if (options.ocr) {
            workers.execute {
                var reader: CardTitleOcr? = null
                try {
                    val activeReader = CardTitleOcr().also { reader = it }
                    activeReader.recognize(card, enhanced = options.rulesScanner) { result, error ->
                        reader?.close()
                        if (error != null) fail("OCR nombre", error)
                        synchronized(lock) { title = result?.lines.orEmpty() }
                        repository.matchLocalPhotoText(result?.lines.orEmpty(), fullTitleDictionary = options.rulesScanner) { matches ->
                            synchronized(lock) {
                                names = matches.map { it.canonicalName }.distinct()
                                ocrMs = maxOf(ocrMs, SystemClock.elapsedRealtime() - started)
                            }
                            completion.finish("title")
                        }
                    }
                } catch (error: Throwable) { reader?.close(); fail("OCR nombre", error); completion.finish("title") }
            }
            workers.execute {
                var reader: PrintingLineOcr? = null
                try {
                    val activeReader = PrintingLineOcr().also { reader = it }
                    val setCodes = CardDatabase.get(app).cardDao().magicSets().mapTo(HashSet()) { it.code.uppercase(Locale.ROOT) }
                    activeReader.recognize(card, retainSpatialLines = options.rulesScanner) { result, error ->
                        reader?.close()
                        if (error != null) fail("OCR impresión", error)
                        try {
                            synchronized(lock) {
                                printing = PrintingMetadataParser.parse(result?.rawText.orEmpty(), setCodes)
                                if (options.rulesScanner) structuredPrinting = StructuredPrintingEvidence.read(result?.spatialLines.orEmpty(), setCodes)
                                ocrMs = maxOf(ocrMs, SystemClock.elapsedRealtime() - started)
                            }
                        } catch (failure: Exception) { fail("OCR impresión", failure) }
                        finally {
                            result?.preview?.recycle()
                            completion.finish("printing")
                        }
                    }
                } catch (error: Throwable) { reader?.close(); fail("OCR impresión", error); completion.finish("printing") }
            }
        }
        if (options.language) {
            workers.execute {
                var detector: CardTextLanguageDetector? = null
                val languageStarted = SystemClock.elapsedRealtime()
                try {
                    val activeDetector = CardTextLanguageDetector().also { detector = it }
                    activeDetector.detect(card, "") { detected ->
                        activeDetector.close()
                        synchronized(lock) {
                            language = detected
                            languageMs = SystemClock.elapsedRealtime() - languageStarted
                        }
                        completion.finish("language")
                    }
                } catch (error: Throwable) {
                    detector?.close()
                    synchronized(lock) { languageMs = SystemClock.elapsedRealtime() - languageStarted }
                    fail("idioma", error)
                    completion.finish("language")
                }
            }
        }
    }

    fun close() {
        closed = true
        client.dispatcher.cancelAll()
        // Let already queued bitmap consumers finish; never recycle underneath an OCR callback.
        workers.shutdown()
        workers.executeWhenTerminated { symbolHashes.close(); symbols.close() }
    }
}

private fun java.util.concurrent.ExecutorService.executeWhenTerminated(block: () -> Unit) {
    Thread {
        try { while (!awaitTermination(1, TimeUnit.DAYS)) { /* wait for active comparisons */ } }
        finally { block() }
    }.apply { isDaemon = true; name = "hash-scanner-close" }.start()
}
