package io.asv.mtgocr.ocrreader.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class CardEditionOption(
    val printingUuid: String,
    val cardName: String,
    val displayName: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val releaseDate: String,
    val rarity: String,
    val finish: String,
    val isFoil: Boolean,
    val imageUrl: String?,
    val typeLine: String,
    val rulesText: String,
    val price: Double?,
    val currency: String?,
    val priceProvider: String?,
    val priceDate: String?
)

data class SetCardOption(
    val printingUuid: String,
    val cardName: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val finish: String,
    val imageUrl: String?,
    val typeLine: String,
    val rulesText: String,
    val price: Double?,
    val currency: String?
)

data class LocalCardNameMatch(
    val canonicalName: String,
    val displayName: String
)

data class CardNameSuggestion(
    val displayName: String,
    val canonicalName: String,
    val language: String
)

class CardRepository private constructor(context: Context) {
    private val dao = CardDatabase.get(context).cardDao()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val imageProvider = ScryfallImageDataProvider(client)
    private val catalog = MtgJsonCatalogDataProvider(dao, client, imageProvider)
    private val priceProvider = MtgJsonPriceDataProvider(context.applicationContext, dao, client)
    private val nameResolver = MtgJsonCardNameResolver(context.applicationContext, dao, client)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadCard(cardName: String, forcePriceRefresh: Boolean = false, callback: (List<CardEditionOption>, Throwable?) -> Unit) {
        executor.execute {
            try {
                var resolution = nameResolver.cached(cardName)
                var canonicalName = resolution?.canonicalName ?: cardName
                val printings = try {
                    catalog.editions(canonicalName)
                } catch (firstError: Exception) {
                    if (resolution != null) throw firstError
                    resolution = nameResolver.resolve(cardName)
                        ?: throw IllegalArgumentException("No se encontró '$cardName' en el catálogo multidioma")
                    canonicalName = resolution.canonicalName
                    catalog.editions(canonicalName)
                }
                if (printings.isEmpty()) error("No se encontró '$cardName' en MTGJSON")
                if (resolution == null) {
                    nameResolver.remember(cardName, printings.first().name)
                    resolution = nameResolver.cached(cardName)
                }
                val prices = try {
                    priceProvider.prices(printings.map { it.uuid }.toSet(), forcePriceRefresh)
                } catch (_: Exception) {
                    dao.pricesFor(printings.map { it.uuid })
                }
                val options = combine(
                    printings,
                    prices,
                    resolution?.displayName ?: printings.first().name
                )
                mainHandler.post { callback(options, null) }
            } catch (error: Throwable) {
                mainHandler.post { callback(emptyList(), error) }
            }
        }
    }

    fun selectedEdition(collectionItemId: String, callback: (OwnedPrintingEntity?) -> Unit) {
        executor.execute {
            val selected = dao.ownedPrinting(collectionItemId)
            mainHandler.post { callback(selected) }
        }
    }

    fun ownedPrintingUuids(callback: (Set<String>) -> Unit) {
        executor.execute {
            val uuids = dao.ownedPrintings().map { it.printingUuid }.toSet()
            mainHandler.post { callback(uuids) }
        }
    }

    /** Local-only OCR lookup: never downloads AtomicCards and never calls a remote search API. */
    fun matchLocalOcrText(candidates: List<String>, callback: (LocalCardNameMatch?) -> Unit) {
        executor.execute {
            val resolution = runCatching { nameResolver.resolveLocalOcrCandidates(candidates) }.getOrNull()
            val match = resolution?.let { LocalCardNameMatch(it.canonicalName, it.displayName) }
            mainHandler.post { callback(match) }
        }
    }

    fun prepareCardNamePredictor(callback: (Boolean) -> Unit = {}) {
        executor.execute {
            val ready = runCatching { nameResolver.preparePredictionIndex() }.getOrDefault(false)
            mainHandler.post { callback(ready) }
        }
    }

    fun suggestCardNames(query: String, callback: (List<CardNameSuggestion>) -> Unit) {
        executor.execute {
            runCatching { nameResolver.preparePredictionIndex() }
            val suggestions = runCatching { nameResolver.suggestions(query, 6) }
                .getOrDefault(emptyList())
                .map { CardNameSuggestion(it.displayName, it.canonicalName, it.language) }
            mainHandler.post { callback(suggestions) }
        }
    }

    fun loadSet(setCode: String, callback: (List<SetCardOption>, Throwable?) -> Unit) {
        executor.execute {
            try {
                val printings = catalog.setCards(setCode)
                if (printings.isEmpty()) error("No se encontraron cartas para el set $setCode")
                val prices = try {
                    priceProvider.prices(printings.map { it.uuid }.toSet())
                } catch (_: Exception) {
                    dao.pricesFor(printings.map { it.uuid })
                }
                val pricesByPrinting = prices.groupBy { it.printingUuid }
                val cards = printings.map { printing ->
                    val availablePrices = pricesByPrinting[printing.uuid].orEmpty()
                    val finishes = printing.finishes.split(',').filter { it.isNotBlank() }
                    val finish = if ("nonfoil" in finishes) "nonfoil" else finishes.firstOrNull() ?: "nonfoil"
                    val priceFinish = if (finish == "nonfoil") "normal" else finish
                    val preferredPrice = availablePrices.firstOrNull { it.finish == priceFinish }
                        ?: availablePrices.firstOrNull()
                    SetCardOption(
                        printing.uuid,
                        printing.name,
                        printing.setCode,
                        printing.setName,
                        printing.collectorNumber,
                        finish,
                        printing.imageUrl,
                        printing.typeLine,
                        printing.rulesText,
                        preferredPrice?.amount,
                        preferredPrice?.currency
                    )
                }
                mainHandler.post { callback(cards, null) }
            } catch (error: Throwable) {
                mainHandler.post { callback(emptyList(), error) }
            }
        }
    }

    fun loadImageLanguages(
        setCode: String,
        collectorNumber: String,
        callback: (List<CardImageVariant>, Throwable?) -> Unit
    ) {
        executor.execute {
            try {
                val variants = imageProvider.getImageLanguages(setCode, collectorNumber)
                mainHandler.post { callback(variants, null) }
            } catch (error: Throwable) {
                mainHandler.post { callback(emptyList(), error) }
            }
        }
    }

    fun selectPrinting(
        collectionItemId: String,
        cardName: String,
        printingUuid: String,
        finish: String,
        callback: () -> Unit = {}
    ) {
        executor.execute {
            dao.saveOwnedPrinting(
                OwnedPrintingEntity(collectionItemId, cardName, printingUuid, finish, System.currentTimeMillis())
            )
            mainHandler.post(callback)
        }
    }

    fun selectEdition(collectionItemId: String, option: CardEditionOption, callback: () -> Unit) {
        executor.execute {
            dao.saveOwnedPrinting(
                OwnedPrintingEntity(
                    collectionItemId,
                    option.cardName,
                    option.printingUuid,
                    option.finish,
                    System.currentTimeMillis()
                )
            )
            mainHandler.post(callback)
        }
    }

    private fun combine(
        printings: List<CardPrintingEntity>,
        prices: List<CardPriceEntity>,
        displayName: String
    ): List<CardEditionOption> {
        val pricesByPrinting = prices.groupBy { it.printingUuid }
        return printings.flatMap { printing ->
            val finishes = printing.finishes.split(',').filter { it.isNotBlank() }.ifEmpty { listOf("nonfoil") }
            finishes.map { finish ->
                val priceFinish = if (finish == "nonfoil") "normal" else finish
                val price = pricesByPrinting[printing.uuid]?.firstOrNull { it.finish == priceFinish }
                CardEditionOption(
                    printingUuid = printing.uuid,
                    cardName = printing.name,
                    displayName = displayName,
                    setCode = printing.setCode,
                    setName = printing.setName,
                    collectorNumber = printing.collectorNumber,
                    releaseDate = printing.releaseDate,
                    rarity = printing.rarity,
                    finish = finish,
                    isFoil = finish == "foil" || finish == "etched",
                    imageUrl = printing.imageUrl,
                    typeLine = printing.typeLine,
                    rulesText = printing.rulesText,
                    price = price?.amount,
                    currency = price?.currency,
                    priceProvider = price?.provider,
                    priceDate = price?.priceDate
                )
            }
        }
    }

    companion object {
        @Volatile private var instance: CardRepository? = null
        @JvmStatic fun get(context: Context): CardRepository = instance ?: synchronized(this) {
            instance ?: CardRepository(context.applicationContext).also { instance = it }
        }
    }
}
