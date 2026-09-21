package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.MtgJsonCatalogDataProvider
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Owns each rectified bitmap until every enabled analysis has completed. No collection writes. */
internal class HashScanAnalysis(context: Context) {
    data class Options(val ocr: Boolean, val symbol: Boolean)
    data class Edition(val code: String, val name: String, val year: Int?)
    data class Row(val candidate: ArtHashMatcher.Candidate, val editions: List<Edition>)
    data class Result(
        val hash: ArtHashMatcher.Result?, val rows: List<Row>, val rawTitle: List<String>,
        val names: List<String>, val printing: PrintingMetadataGuess?,
        val symbols: SetSymbolShapeMatch?, val errors: List<String>,
        val elapsedMs: Long, val ocrMs: Long, val symbolMs: Long
    )

    private val app = context.applicationContext
    private val repository by lazy { CardRepository.get(app) }
    private val workers = Executors.newFixedThreadPool(3)
    private val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    private val symbols = SetSymbolShapeMatcher(app, client)
    @Volatile private var matcher: ArtHashMatcher? = null
    @Volatile private var closed = false

    fun prepare(ocr: Boolean, callback: (Boolean) -> Unit) {
        workers.execute {
            val ready = runCatching {
                if (matcher == null) matcher = ArtHashMatcher(ArtHashIndex.read(app.assets.open(ArtHashIndex.ASSET)))
            }.isSuccess
            if (closed) return@execute
            if (ocr && ready) {
                try { repository.prepareCardNamePredictor { callback(it && !closed) } }
                catch (_: Exception) { callback(false) }
            } else callback(ready)
        }
    }

    fun analyze(card: Bitmap, options: Options, callback: (Result) -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val lock = Any()
        var hash: ArtHashMatcher.Result? = null
        var rows = emptyList<Row>()
        var title = emptyList<String>()
        var names = emptyList<String>()
        var printing: PrintingMetadataGuess? = null
        var shape: SetSymbolShapeMatch? = null
        var ocrMs = 0L
        var symbolMs = 0L
        val errors = mutableListOf<String>()
        fun fail(stage: String, error: Throwable) = synchronized(lock) {
            errors.add("$stage: ${error.message ?: error.javaClass.simpleName}")
        }
        val completion = HashScanCompletion(options.ocr) {
            card.recycle()
            val result = synchronized(lock) {
                Result(hash, rows, title, names, printing, shape, errors.toList(),
                    SystemClock.elapsedRealtime() - started, ocrMs, symbolMs)
            }
            if (!closed) callback(result)
        }
        // Hash and both OCR passes start independently. Symbol templates are restricted to the
        // hash candidates' cached printings, then compared while OCR is still running.
        workers.execute {
            try {
                val matched = checkNotNull(matcher).match(card)
                synchronized(lock) {
                    hash = matched
                    rows = matched.candidates.map { Row(it, emptyList()) }
                }
                val dao = if (options.ocr || options.symbol) CardDatabase.get(app).cardDao() else null
                val sets = if (options.ocr || options.symbol) dao!!.magicSets().associateBy { it.code.uppercase(Locale.ROOT) }
                    else emptyMap()
                val candidateRows = matched.candidates.map { candidate ->
                    val codes = if (options.ocr || options.symbol) {
                        dao!!.printingsByName(MtgJsonCatalogDataProvider.normalize(candidate.hit.name))
                            .map { it.setCode.uppercase(Locale.ROOT) } + candidate.hit.setCode.uppercase(Locale.ROOT)
                    } else emptyList()
                    Row(candidate, codes.distinct().map { code ->
                        Edition(code, sets[code]?.name ?: code, sets[code]?.releaseDate?.take(4)?.toIntOrNull())
                    })
                }
                synchronized(lock) { rows = candidateRows }
                if (options.symbol && !closed) {
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
                    activeReader.recognize(card) { result, error ->
                        reader?.close()
                        if (error != null) fail("OCR nombre", error)
                        synchronized(lock) { title = result?.lines.orEmpty() }
                        repository.matchLocalPhotoText(result?.lines.orEmpty()) { matches ->
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
                    activeReader.recognize(card) { result, error ->
                        reader?.close()
                        if (error != null) fail("OCR impresión", error)
                        try {
                            synchronized(lock) {
                                printing = PrintingMetadataParser.parse(result?.rawText.orEmpty(), setCodes)
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
    }

    fun close() {
        closed = true
        client.dispatcher.cancelAll()
        // Let already queued bitmap consumers finish; never recycle underneath an OCR callback.
        workers.shutdown()
        workers.executeWhenTerminated { symbols.close() }
    }
}

private fun java.util.concurrent.ExecutorService.executeWhenTerminated(block: () -> Unit) {
    Thread {
        try { while (!awaitTermination(1, TimeUnit.DAYS)) { /* wait for active comparisons */ } }
        finally { block() }
    }.apply { isDaemon = true; name = "hash-scanner-close" }.start()
}
