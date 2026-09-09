package io.asv.mtgocr.ocrreader.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** EUR prices for exact Scryfall printings, fetched in batches of at most 75 identifiers. */
class ScryfallPriceDataProvider(private val client: OkHttpClient) {
    fun prices(printings: List<CardPrintingEntity>): List<CardPriceEntity> {
        val byScryfallId = printings.mapNotNull { printing ->
            printing.scryfallId?.takeIf { it.isNotBlank() }?.let { it to printing }
        }.toMap()
        if (byScryfallId.isEmpty()) return emptyList()
        val result = mutableListOf<CardPriceEntity>()
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(now))
        byScryfallId.keys.chunked(75).forEachIndexed { batchIndex, ids ->
            if (batchIndex > 0) Thread.sleep(100L)
            val identifiers = JSONArray()
            ids.forEach { identifiers.put(JSONObject().put("id", it)) }
            val body = JSONObject().put("identifiers", identifiers).toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("https://api.scryfall.com/cards/collection")
                .header("User-Agent", ScryfallImageDataProvider.USER_AGENT)
                .header("Accept", "application/json;q=0.9,*/*;q=0.8")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Scryfall precios devolvió HTTP ${response.code}")
                val cards = JSONObject(response.body?.string().orEmpty()).getJSONArray("data")
                for (index in 0 until cards.length()) {
                    val card = cards.getJSONObject(index)
                    val printing = byScryfallId[card.optString("id")] ?: continue
                    val values = card.optJSONObject("prices") ?: continue
                    values.optString("eur").toDoubleOrNull()?.let { amount ->
                        result += CardPriceEntity(printing.uuid, "normal", amount, "EUR", "scryfall", date, now)
                    }
                    values.optString("eur_foil").toDoubleOrNull()?.let { amount ->
                        result += CardPriceEntity(printing.uuid, "foil", amount, "EUR", "scryfall", date, now)
                    }
                }
            }
        }
        return result
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
