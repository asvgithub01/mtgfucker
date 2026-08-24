package io.asv.mtgocr.ocrreader.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class ScryfallPrintingHint(
    val scryfallId: String,
    val name: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val releasedAt: String,
    val finishes: List<String>,
    val imageUrl: String?
)

data class CardImageVariant(
    val languageCode: String,
    val printedName: String,
    val imageUrl: String
)

/** Scryfall is deliberately responsible only for printing discovery and card imagery. */
class ScryfallImageDataProvider(private val client: OkHttpClient) {
    fun getPrintingImages(cardName: String): List<ScryfallPrintingHint> {
        val results = mutableListOf<ScryfallPrintingHint>()
        var nextUrl: String? = "https://api.scryfall.com/cards/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "!\"${cardName.trim()}\" game:paper")
            .addQueryParameter("unique", "prints")
            .addQueryParameter("order", "released")
            .addQueryParameter("dir", "desc")
            .build().toString()

        while (nextUrl != null) {
            val request = Request.Builder()
                .url(nextUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Scryfall devolvió HTTP ${response.code}")
                val root = JSONObject(response.body?.string().orEmpty())
                val data = root.getJSONArray("data")
                for (index in 0 until data.length()) {
                    val card = data.getJSONObject(index)
                    val image = when {
                        card.has("image_uris") -> card.getJSONObject("image_uris").optString("normal").ifBlank { null }
                        card.has("card_faces") -> card.getJSONArray("card_faces")
                            .optJSONObject(0)?.optJSONObject("image_uris")?.optString("normal")?.ifBlank { null }
                        else -> null
                    }
                    val finishesJson = card.optJSONArray("finishes")
                    val finishes = buildList {
                        if (finishesJson != null) for (i in 0 until finishesJson.length()) add(finishesJson.getString(i))
                    }
                    results += ScryfallPrintingHint(
                        scryfallId = card.getString("id"),
                        name = card.getString("name"),
                        setCode = card.getString("set").uppercase(),
                        setName = card.getString("set_name"),
                        collectorNumber = card.getString("collector_number"),
                        releasedAt = card.optString("released_at"),
                        finishes = finishes,
                        imageUrl = image
                    )
                }
                nextUrl = if (root.optBoolean("has_more")) root.optString("next_page").ifBlank { null } else null
            }
        }
        return results
    }

    fun getSetImages(setCode: String): List<ScryfallPrintingHint> {
        return searchPrintings("set:${setCode.lowercase()} game:paper", includeMultilingual = false)
    }

    fun getImageLanguages(setCode: String, collectorNumber: String): List<CardImageVariant> {
        val rootUrl = "https://api.scryfall.com/cards/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "set:${setCode.lowercase()} cn:\"$collectorNumber\"")
            .addQueryParameter("unique", "prints")
            .addQueryParameter("include_multilingual", "true")
            .build().toString()
        val variants = mutableListOf<CardImageVariant>()
        var nextUrl: String? = rootUrl
        while (nextUrl != null) {
            val request = Request.Builder().url(nextUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Scryfall devolvió HTTP ${response.code}")
                val root = JSONObject(response.body?.string().orEmpty())
                val data = root.getJSONArray("data")
                for (index in 0 until data.length()) {
                    val card = data.getJSONObject(index)
                    val image = cardImage(card, "large") ?: continue
                    variants += CardImageVariant(
                        card.optString("lang", "en"),
                        card.optString("printed_name", card.optString("name")),
                        image
                    )
                }
                nextUrl = if (root.optBoolean("has_more")) root.optString("next_page").ifBlank { null } else null
            }
        }
        return variants.distinctBy { it.languageCode }
    }

    private fun searchPrintings(query: String, includeMultilingual: Boolean): List<ScryfallPrintingHint> {
        val results = mutableListOf<ScryfallPrintingHint>()
        var nextUrl: String? = "https://api.scryfall.com/cards/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("unique", "prints")
            .addQueryParameter("order", "set")
            .addQueryParameter("include_multilingual", includeMultilingual.toString())
            .build().toString()
        while (nextUrl != null) {
            val request = Request.Builder().url(nextUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Scryfall devolvió HTTP ${response.code}")
                val root = JSONObject(response.body?.string().orEmpty())
                val data = root.getJSONArray("data")
                for (index in 0 until data.length()) {
                    val card = data.getJSONObject(index)
                    results += card.toPrintingHint()
                }
                nextUrl = if (root.optBoolean("has_more")) root.optString("next_page").ifBlank { null } else null
            }
        }
        return results
    }

    private fun JSONObject.toPrintingHint(): ScryfallPrintingHint {
        val finishesJson = optJSONArray("finishes")
        val finishes = buildList {
            if (finishesJson != null) for (i in 0 until finishesJson.length()) add(finishesJson.getString(i))
        }
        return ScryfallPrintingHint(
            scryfallId = getString("id"),
            name = getString("name"),
            setCode = getString("set").uppercase(),
            setName = getString("set_name"),
            collectorNumber = getString("collector_number"),
            releasedAt = optString("released_at"),
            finishes = finishes,
            imageUrl = cardImage(this)
        )
    }

    private fun cardImage(card: JSONObject, preferredSize: String = "normal"): String? = when {
        card.has("image_uris") -> card.getJSONObject("image_uris").let { images ->
            images.optString(preferredSize).ifBlank { images.optString("normal") }.ifBlank { null }
        }
        card.has("card_faces") -> card.getJSONArray("card_faces")
            .optJSONObject(0)?.optJSONObject("image_uris")?.let { images ->
                images.optString(preferredSize).ifBlank { images.optString("normal") }.ifBlank { null }
            }
        else -> null
    }

    companion object {
        const val USER_AGENT = "MTGOcrCollection/2.0 (Android; contact: github.com/asvgithub01/mtgfucker)"
    }
}
