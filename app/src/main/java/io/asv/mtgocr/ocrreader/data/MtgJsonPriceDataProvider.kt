package io.asv.mtgocr.ocrreader.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.GzipSource
import okio.buffer
import okio.source
import java.io.File
import java.security.MessageDigest

internal object PermanentPriceCachePolicy {
    /** Partial cached rows never prove that every UUID in a request was inspected. */
    fun canServeCached(forceRefresh: Boolean, exactRequestWasScanned: Boolean): Boolean =
        !forceRefresh && exactRequestWasScanned
}

/** Prices are intentionally refreshable and isolated from the stable card catalog provider. */
class MtgJsonPriceDataProvider(
    context: Context,
    private val dao: CardDao,
    private val client: OkHttpClient
) {
    // This is intentional app data, not an evictable HTTP cache. It remains available until the
    // user clears app data or explicitly asks to refresh prices.
    private val cacheDirectory = File(context.filesDir, "mtgjson").apply { mkdirs() }
    private val priceFile = File(cacheDirectory, "AllPricesToday.json.gz").also { persistent ->
        val legacy = File(File(context.cacheDir, "mtgjson"), persistent.name)
        if (!persistent.exists() && legacy.isFile) runCatching { legacy.copyTo(persistent) }
    }
    private val scanPreferences = context.getSharedPreferences("mtgjson_price_scans", Context.MODE_PRIVATE)

    @Synchronized
    fun prices(printingUuids: Set<String>, forceRefresh: Boolean = false): List<CardPriceEntity> {
        if (printingUuids.isEmpty()) return emptyList()
        val cached = dao.pricesFor(printingUuids.toList())
        // AllPricesToday is small on disk only because it is gzipped. Reading it means inflating
        // and walking the complete global price map, so never do that again for a card that is
        // already cached. The explicit "Actualizar precios" action is the refresh boundary.
        // A few cached rows do not mean the complete set was scanned. Only the exact UUID group
        // marker proves that the snapshot was walked for every requested printing.
        if (PermanentPriceCachePolicy.canServeCached(forceRefresh, wasScanned(printingUuids))) return cached

        if (forceRefresh || !priceFile.exists()) downloadPriceSnapshot()

        val parsed = priceFile.source().buffer().use { compressed ->
            GzipSource(compressed).buffer().use { source ->
                MtgJsonParsers.readPrices(source, printingUuids)
            }
        }
        val now = System.currentTimeMillis()
        val entities = parsed.map {
            CardPriceEntity(it.printingUuid, it.finish, it.amount, it.currency, it.provider, it.date, now)
        }
        if (entities.isNotEmpty()) dao.savePrices(entities)
        markScanned(printingUuids)
        return dao.pricesFor(printingUuids.toList())
    }

    private fun wasScanned(printingUuids: Set<String>): Boolean =
        scanPreferences.contains(scanKey(printingUuids))

    private fun markScanned(printingUuids: Set<String>) {
        scanPreferences.edit().putLong(scanKey(printingUuids), System.currentTimeMillis()).apply()
    }

    private fun scanKey(printingUuids: Set<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(printingUuids.sorted().joinToString("|").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "scan_$digest"
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
}
