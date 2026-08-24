package io.asv.mtgocr.ocrreader.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Card metadata comes from MTGJSON set files and is cached in Room. */
class MtgJsonCatalogDataProvider(
    private val dao: CardDao,
    private val client: OkHttpClient,
    private val imageProvider: ScryfallImageDataProvider
) {
    fun editions(cardName: String): List<CardPrintingEntity> {
        val normalizedName = normalize(cardName)
        val cached = dao.printingsByName(normalizedName)
        return try {
            val imageHints = imageProvider.getPrintingImages(cardName)
            if (imageHints.isEmpty()) return cached
            val syncBySet = dao.syncedSets(normalizedName).associateBy { it.setCode }
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
        fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}
