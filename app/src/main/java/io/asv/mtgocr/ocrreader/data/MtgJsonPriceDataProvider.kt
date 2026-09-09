package io.asv.mtgocr.ocrreader.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.GzipSource
import okio.buffer
import okio.source
import java.io.File

/** Prices are intentionally refreshable and isolated from the stable card catalog provider. */
class MtgJsonPriceDataProvider(
    context: Context,
    private val dao: CardDao,
    private val client: OkHttpClient
) {
    private val appContext = context.applicationContext
    // This is intentional app data, not an evictable HTTP cache. It remains available until the
    // user clears app data or explicitly asks to refresh prices.
    private val cacheDirectory = File(context.filesDir, "mtgjson").apply { mkdirs() }
    private val priceFile = File(cacheDirectory, "AllPricesToday.json.gz").also { persistent ->
        val legacy = File(File(context.cacheDir, "mtgjson"), persistent.name)
        if (!persistent.exists() && legacy.isFile) runCatching { legacy.copyTo(persistent) }
    }
    private val indexPreferences = context.getSharedPreferences("mtgjson_price_index", Context.MODE_PRIVATE)
    @Volatile private var indexReady = indexPreferences.getBoolean(KEY_INDEX_READY, false)

    /** Downloads once and builds the complete UUID/provider/finish index before scans need it. */
    @Synchronized
    fun prepare(forceRefresh: Boolean = false) {
        if (!forceRefresh && indexReady) return
        if (forceRefresh || !priceFile.exists()) downloadPriceSnapshot()
        indexPriceSnapshot()
    }

    @Synchronized
    fun prices(
        printingUuids: Set<String>,
        forceRefresh: Boolean = false,
        providerOrder: List<String> = PriceSourcePreferences.priorityIds(appContext)
    ): List<CardPriceEntity> {
        if (printingUuids.isEmpty()) return emptyList()
        prepare(forceRefresh)
        val priority = providerOrder.withIndex().associate { it.value to it.index }
        val selected = snapshotPricesFor(printingUuids)
            .groupBy { it.printingUuid to it.finish }
            .mapNotNull { (_, candidates) ->
                candidates.minByOrNull { priority[it.provider] ?: Int.MAX_VALUE }
            }
        val entities = selected.map {
            CardPriceEntity(
                it.printingUuid,
                it.finish,
                it.amount,
                it.currency,
                it.provider,
                it.priceDate,
                it.updatedAt
            )
        }
        val cached = cardPricesFor(printingUuids)
        if (entities.toSet() == cached.toSet()) return cached
        printingUuids.chunked(SQLITE_IN_BATCH_SIZE).forEach(dao::deletePricesFor)
        if (entities.isNotEmpty()) dao.savePrices(entities)
        return cardPricesFor(printingUuids)
    }

    /** Reads the already indexed snapshot only; it never downloads or waits for a web provider. */
    fun cachedPrices(
        printingUuids: Set<String>,
        providerOrder: List<String>
    ): List<CardPriceEntity> {
        if (printingUuids.isEmpty()) return emptyList()
        val priority = providerOrder.withIndex().associate { it.value to it.index }
        if (priority.isEmpty()) return cardPricesFor(printingUuids)
        val snapshot = snapshotPricesFor(printingUuids)
            .groupBy { it.printingUuid to it.finish }
            .mapNotNull { (_, candidates) ->
                candidates
                    .filter { priority.containsKey(it.provider) }
                    .minByOrNull { priority[it.provider] ?: Int.MAX_VALUE }
            }
            .map {
                CardPriceEntity(
                    it.printingUuid,
                    it.finish,
                    it.amount,
                    it.currency,
                    it.provider,
                    it.priceDate,
                    it.updatedAt
                )
            }
        return snapshot.ifEmpty { cardPricesFor(printingUuids) }
    }

    // Basic lands and cards with many promos can exceed SQLite's bind-variable limit if every
    // printing UUID is sent through one IN clause. Keep the foreground scan lookup bounded.
    private fun snapshotPricesFor(printingUuids: Set<String>): List<PriceSnapshotEntity> =
        printingUuids.chunked(SQLITE_IN_BATCH_SIZE).flatMap(dao::snapshotPricesFor)

    private fun cardPricesFor(printingUuids: Set<String>): List<CardPriceEntity> =
        printingUuids.chunked(SQLITE_IN_BATCH_SIZE).flatMap(dao::pricesFor)

    private fun indexPriceSnapshot() {
        val startedAt = SystemClock.elapsedRealtime()
        val snapshotTimestamp = System.currentTimeMillis()
        var imported = 0
        priceFile.source().buffer().use { compressed ->
            GzipSource(compressed).buffer().use { source ->
                MtgJsonParsers.streamAllPrices(source) { batch ->
                    dao.savePriceSnapshot(batch.map {
                        PriceSnapshotEntity(
                            it.printingUuid,
                            it.finish,
                            it.provider,
                            it.amount,
                            it.currency,
                            it.date,
                            snapshotTimestamp
                        )
                    })
                    imported += batch.size
                }
            }
        }
        check(imported > 0) { "El snapshot de precios de MTGJSON estaba vacío" }
        // Old rows remain usable if parsing fails. They are removed only after a complete import.
        dao.deleteOldPriceSnapshot(snapshotTimestamp)
        indexReady = true
        indexPreferences.edit()
            .putBoolean(KEY_INDEX_READY, true)
            .putLong(KEY_INDEX_UPDATED_AT, snapshotTimestamp)
            .putInt(KEY_INDEX_ROW_COUNT, imported)
            .apply()
        Log.i(TAG, "Índice local de precios preparado: $imported filas en ${SystemClock.elapsedRealtime() - startedAt} ms")
    }

    private fun downloadPriceSnapshot() {
        val request = Request.Builder()
            .url("https://mtgjson.com/api/v5/AllPricesToday.json.gz")
            .header("User-Agent", ScryfallImageDataProvider.USER_AGENT)
            .header("Accept", "application/octet-stream")
            .build()
        val temporary = File(cacheDirectory, "AllPricesToday.json.gz.part")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("MTGJSON precios devolvió HTTP ${response.code}")
            val body = response.body ?: error("MTGJSON precios devolvió una respuesta vacía")
            temporary.outputStream().buffered().use { output -> body.byteStream().copyTo(output) }
        }
        if (priceFile.exists()) priceFile.delete()
        check(temporary.renameTo(priceFile)) { "No se pudo guardar el fichero de precios de MTGJSON" }
    }

    private companion object {
        const val TAG = "MtgJsonPrices"
        const val SQLITE_IN_BATCH_SIZE = 400
        const val KEY_INDEX_READY = "ready"
        const val KEY_INDEX_UPDATED_AT = "updated_at"
        const val KEY_INDEX_ROW_COUNT = "row_count"
    }
}
