package io.asv.mtgocr.ocrreader.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Card metadata comes from MTGJSON set files and is cached in Room. */
class MtgJsonCatalogDataProvider(
    private val dao: CardDao,
    private val client: OkHttpClient,
    private val imageProvider: ScryfallImageDataProvider
) {
    fun sets(forceRefresh: Boolean = false): List<MagicSetEntity> {
        val cached = dao.magicSets()
        val syncedAt = dao.magicSetCatalogSync()?.updatedAt ?: 0L
        if (!forceRefresh && cached.isNotEmpty() &&
            System.currentTimeMillis() - syncedAt < SET_CATALOG_TTL_MS
        ) return cached

        return try {
            val request = Request.Builder()
                .url("https://mtgjson.com/api/v5/SetList.json")
                .header("User-Agent", ScryfallImageDataProvider.USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val entities = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("MTGJSON devolvió HTTP ${response.code} para el catálogo")
                val root = JSONObject(response.body?.string().orEmpty())
                val data = root.getJSONArray("data")
                val now = System.currentTimeMillis()
                buildList {
                    for (index in 0 until data.length()) {
                        val set = data.getJSONObject(index)
                        val type = set.optString("type")
                        if (set.optBoolean("isOnlineOnly") || type in EXCLUDED_CATALOG_TYPES) continue
                        val code = set.optString("code").uppercase(Locale.US)
                        val name = set.optString("name")
                        val releaseDate = set.optString("releaseDate")
                        val count = set.optInt("totalSetSize", set.optInt("baseSetSize"))
                        if (code.isBlank() || name.isBlank() || releaseDate.isBlank() || count <= 0) continue
                        add(MagicSetEntity(code, name, releaseDate, type, count, now))
                    }
                }
            }
            if (entities.isNotEmpty()) {
                dao.saveMagicSets(entities)
                dao.saveSetSync(CardSetSyncEntity("__catalog__", "*", System.currentTimeMillis()))
            }
            dao.magicSets().ifEmpty { cached }
        } catch (error: Exception) {
            if (cached.isNotEmpty()) cached else throw error
        }
    }

    fun editions(cardName: String): List<CardPrintingEntity> {
        val normalizedName = normalize(cardName)
        val cached = dao.printingsByName(normalizedName)
        val discoveryStaleBefore = System.currentTimeMillis() - CARD_DISCOVERY_TTL_MS
        val previousSetSyncs = dao.syncedSets(normalizedName).filter { it.setCode != CARD_DISCOVERY_SYNC_CODE }
        // Discovering printings is not a local query: it pages through Scryfall and may then
        // download several complete MTGJSON set files. Reusing a recent discovery makes opening a
        // card effectively a Room lookup instead of repeating that network fan-out every time.
        val discoveryIsFresh = (dao.cardDiscoverySync(normalizedName)?.updatedAt ?: 0L) >= discoveryStaleBefore
        val legacySetSyncIsFresh = previousSetSyncs.isNotEmpty() &&
            previousSetSyncs.all { it.updatedAt >= discoveryStaleBefore }
        if (cached.isNotEmpty() && (discoveryIsFresh || legacySetSyncIsFresh)) {
            if (!discoveryIsFresh) {
                // Upgrade the per-set cache produced by older builds to the new discovery marker.
                dao.saveSetSync(CardSetSyncEntity(normalizedName, CARD_DISCOVERY_SYNC_CODE, System.currentTimeMillis()))
            }
            return cached
        }
        return try {
            val imageHints = imageProvider.getPrintingImages(cardName)
            if (imageHints.isEmpty()) return cached
            val syncBySet = previousSetSyncs.associateBy { it.setCode }
            val staleBefore = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            val hintsByScryfall = imageHints.associateBy { it.scryfallId }
            val hintsBySet = imageHints.groupBy { it.setCode }

            var lastSetError: Exception? = null
            for ((setCode, _) in hintsBySet) {
                val sync = syncBySet[setCode]
                if (sync != null && sync.updatedAt >= staleBefore) continue
                try {
                    fetchSet(setCode, cardName, hintsByScryfall)
                    dao.saveSetSync(CardSetSyncEntity(normalizedName, setCode, System.currentTimeMillis()))
                } catch (error: Exception) {
                    // A single unusual Scryfall set code must not hide all other valid MTGJSON editions.
                    lastSetError = error
                }
            }

            // Scryfall image URLs can change independently; refresh them without making it the card-data source.
            val refreshed = dao.printingsByName(normalizedName).map { printing ->
                val hint = printing.scryfallId?.let(hintsByScryfall::get)
                    ?: imageHints.firstOrNull {
                        it.setCode == printing.setCode && it.collectorNumber == printing.collectorNumber
                    }
                if (hint?.imageUrl != null && hint.imageUrl != printing.imageUrl) printing.copy(imageUrl = hint.imageUrl) else printing
            }
            if (refreshed.isNotEmpty()) dao.savePrintings(refreshed)
            val result = dao.printingsByName(normalizedName).ifEmpty { cached }
            if (result.isEmpty() && lastSetError != null) throw lastSetError
            if (result.isNotEmpty()) {
                dao.saveSetSync(CardSetSyncEntity(normalizedName, CARD_DISCOVERY_SYNC_CODE, System.currentTimeMillis()))
            }
            result
        } catch (error: Exception) {
            val available = dao.printingsByName(normalizedName)
            when {
                available.isNotEmpty() -> available
                cached.isNotEmpty() -> cached
                else -> throw error
            }
        }
    }

    fun setCards(setCode: String): List<CardPrintingEntity> {
        val normalizedSetCode = setCode.uppercase(Locale.US)
        val cached = dao.printingsBySet(normalizedSetCode)
        val sync = dao.fullSetSync(normalizedSetCode)
        val staleBefore = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        if (cached.isNotEmpty() && sync != null && sync.updatedAt >= staleBefore) return cached
        return try {
            val hints = imageProvider.getSetImages(normalizedSetCode)
            fetchSet(normalizedSetCode, null, hints.associateBy { it.scryfallId })
            dao.saveSetSync(CardSetSyncEntity("*", normalizedSetCode, System.currentTimeMillis()))
            dao.printingsBySet(normalizedSetCode).ifEmpty { cached }
        } catch (error: Exception) {
            val available = dao.printingsBySet(normalizedSetCode)
            if (available.isNotEmpty()) available else throw error
        }
    }

    private fun fetchSet(
        setCode: String,
        cardName: String?,
        hintsByScryfall: Map<String, ScryfallPrintingHint>
    ) {
        val request = Request.Builder()
            .url("https://mtgjson.com/api/v5/${setCode.uppercase(Locale.US)}.json")
            .header("User-Agent", ScryfallImageDataProvider.USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("MTGJSON devolvió HTTP ${response.code} para $setCode")
            val body = response.body ?: error("MTGJSON devolvió una respuesta vacía para $setCode")
            val parsed = MtgJsonParsers.readSet(body.source(), cardName)
            val now = System.currentTimeMillis()
            val entities = parsed.cards
                .filter { it.availability.isEmpty() || "paper" in it.availability }
                .map { card ->
                    val hint = card.scryfallId?.let(hintsByScryfall::get)
                    CardPrintingEntity(
                        uuid = card.uuid,
                        normalizedName = normalize(card.name),
                        name = card.name,
                        setCode = parsed.code.ifBlank { setCode },
                        setName = parsed.name.ifBlank { hint?.setName.orEmpty() },
                        collectorNumber = card.number,
                        releaseDate = parsed.releaseDate.ifBlank { hint?.releasedAt.orEmpty() },
                        rarity = card.rarity,
                        scryfallId = card.scryfallId,
                        finishes = card.finishes.joinToString(","),
                        typeLine = card.type,
                        rulesText = card.text,
                        imageUrl = hint?.imageUrl,
                        updatedAt = now
                    )
                }
            if (entities.isNotEmpty()) dao.savePrintings(entities)
        }
    }

    companion object {
        private const val CARD_DISCOVERY_SYNC_CODE = "*"
        private val CARD_DISCOVERY_TTL_MS = TimeUnit.HOURS.toMillis(24)
        private val SET_CATALOG_TTL_MS = TimeUnit.HOURS.toMillis(24)
        private val EXCLUDED_CATALOG_TYPES = setOf("token", "memorabilia", "minigame")

        fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}
