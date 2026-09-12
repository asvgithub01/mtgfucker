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

data class LocalizedPrintingVariant(
    val setCode: String,
    val collectorNumber: String,
    val languageCode: String,
    val printedName: String,
    val imageUrl: String
)

data class LocalizedCardName(
    val canonicalName: String,
    val printedName: String,
    val languageCode: String
)

/** Scryfall is deliberately responsible only for printing discovery and card imagery. */
class ScryfallImageDataProvider(private val client: OkHttpClient) {
    /** Cheap first-page probe used to choose the scanner OCR script for a locked set. */
    fun setHasLanguage(setCode: String, languageCode: String): Boolean {
        val url = "https://api.scryfall.com/cards/search".toHttpUrl().newBuilder()
            .addQueryParameter(
                "q",
                "set:${setCode.lowercase()} lang:${languageCode.lowercase()} game:paper"
            )
            .addQueryParameter("unique", "prints")
            .addQueryParameter("include_multilingual", "true")
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json;q=0.9,*/*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return false
            if (!response.isSuccessful) error("Scryfall idiomas devolvió HTTP ${response.code}")
            return JSONObject(response.body?.string().orEmpty()).getJSONArray("data").length() > 0
        }
    }

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

    /** Printed names for a set, used to fill gaps in MTGJSON's multilingual name catalog. */
    fun getSetLocalizedNames(setCode: String, languageCode: String): List<LocalizedCardName> {
        val results = mutableListOf<LocalizedCardName>()
        var nextUrl: String? = "https://api.scryfall.com/cards/search".toHttpUrl().newBuilder()
            .addQueryParameter(
                "q",
                "set:${setCode.lowercase()} lang:${languageCode.lowercase()} game:paper"
            )
            .addQueryParameter("unique", "prints")
            .addQueryParameter("order", "set")
            .addQueryParameter("include_multilingual", "true")
            .build().toString()
        while (nextUrl != null) {
            val request = Request.Builder().url(nextUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return emptyList()
                if (!response.isSuccessful) error("Scryfall idiomas devolvió HTTP ${response.code}")
                val root = JSONObject(response.body?.string().orEmpty())
                val data = root.getJSONArray("data")
                for (index in 0 until data.length()) {
                    val card = data.getJSONObject(index)
                    val printedName = card.optString("printed_name").trim()
                    val canonicalName = card.getString("name")
                    val language = card.optString("lang", languageCode)
                    if (printedName.isNotEmpty()) {
                        results += LocalizedCardName(canonicalName, printedName, language)
                    }
                    val faces = card.optJSONArray("card_faces")
                    if (faces != null) for (faceIndex in 0 until faces.length()) {
                        val faceName = faces.getJSONObject(faceIndex)
                            .optString("printed_name").trim()
                        if (faceName.isNotEmpty()) {
                            results += LocalizedCardName(canonicalName, faceName, language)
                        }
                    }
                }
                nextUrl = if (root.optBoolean("has_more")) {
                    root.optString("next_page").ifBlank { null }
                } else null
            }
        }
        return results.distinctBy { MtgJsonParsers.normalizeSearchName(it.printedName) }
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

    /** All physical printings of one card that actually exist in the requested language. */
    fun getLocalizedPrintings(cardName: String, languageCode: String): List<LocalizedPrintingVariant> {
        val rootUrl = "https://api.scryfall.com/cards/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "!\"${cardName.trim()}\" lang:${languageCode.lowercase()} game:paper")
            .addQueryParameter("unique", "prints")
            .addQueryParameter("order", "released")
            .addQueryParameter("dir", "desc")
            .addQueryParameter("include_multilingual", "true")
            .build().toString()
        val variants = mutableListOf<LocalizedPrintingVariant>()
        var nextUrl: String? = rootUrl
        while (nextUrl != null) {
            val request = Request.Builder().url(nextUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return emptyList()
                if (!response.isSuccessful) error("Scryfall idiomas devolvió HTTP ${response.code}")
                val root = JSONObject(response.body?.string().orEmpty())
                val data = root.getJSONArray("data")
                for (index in 0 until data.length()) {
                    val card = data.getJSONObject(index)
                    val image = cardImage(card, "large") ?: continue
                    variants += LocalizedPrintingVariant(
                        card.getString("set").uppercase(),
                        card.getString("collector_number"),
                        card.optString("lang", languageCode),
                        card.optString("printed_name", card.optString("name")),
                        image
                    )
                }
                nextUrl = if (root.optBoolean("has_more")) root.optString("next_page").ifBlank { null } else null
            }
        }
        return variants.distinctBy { it.setCode to it.collectorNumber }
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
