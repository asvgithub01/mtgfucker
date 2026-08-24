package io.asv.mtgocr.ocrreader.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.GzipSource
import okio.buffer
import okio.source
import java.io.File
import java.util.concurrent.TimeUnit

/** Prices are intentionally refreshable and isolated from the stable card catalog provider. */
class MtgJsonPriceDataProvider(
    context: Context,
    private val dao: CardDao,
    private val client: OkHttpClient
) {
    private val cacheDirectory = File(context.cacheDir, "mtgjson").apply { mkdirs() }
    private val priceFile = File(cacheDirectory, "AllPricesToday.json.gz")

    fun prices(printingUuids: Set<String>, forceRefresh: Boolean = false): List<CardPriceEntity> {
        if (printingUuids.isEmpty()) return emptyList()
        val stale = !priceFile.exists() ||
            System.currentTimeMillis() - priceFile.lastModified() > TimeUnit.HOURS.toMillis(24)
        if (forceRefresh || stale) downloadPriceSnapshot()

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
        return dao.pricesFor(printingUuids.toList())
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
