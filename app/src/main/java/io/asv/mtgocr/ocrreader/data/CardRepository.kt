package io.asv.mtgocr.ocrreader.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import okhttp3.OkHttpClient
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
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

data class MagicSetOption(
    val code: String,
    val name: String,
    val releaseDate: String,
    val type: String,
    val cardCount: Int
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
    private val artworkIdentifier = CardArtworkIdentifier(context.applicationContext, client)
    private val catalog = MtgJsonCatalogDataProvider(dao, client, imageProvider)
    private val priceProvider = MtgJsonPriceDataProvider(context.applicationContext, dao, client)
    private val nameResolver = MtgJsonCardNameResolver(context.applicationContext, dao, client)
    private val executor = Executors.newSingleThreadExecutor()
    private val imageExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val optionCache = object : LinkedHashMap<String, List<CardEditionOption>>(48, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<CardEditionOption>>?): Boolean {
            return size > 48
        }
    }

    fun loadSetCatalog(
        forceRefresh: Boolean = false,
        callback: (List<MagicSetOption>, Throwable?) -> Unit
    ) {
        executor.execute {
            try {
                val sets = catalog.sets(forceRefresh).map {
                    MagicSetOption(it.code, it.name, it.releaseDate, it.type, it.cardCount)
                }
                mainHandler.post { callback(sets, null) }
            } catch (error: Throwable) {
                mainHandler.post { callback(emptyList(), error) }
            }
        }
    }

    fun loadCard(
        cardName: String,
        forcePriceRefresh: Boolean = false,
        deliverEditionsBeforePrices: Boolean = false,
        callback: (List<CardEditionOption>, Throwable?) -> Unit
    ): Future<*> {
        if (!forcePriceRefresh) {
            cachedOptions(cardName)?.let { cached ->
                mainHandler.post { callback(cached, null) }
                return CompletedFuture
            }
        }
        return executor.submit {
            val startedAt = SystemClock.elapsedRealtime()
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
                val printingUuids = printings.map { it.uuid }
                val cachedPrices = dao.pricesFor(printingUuids)
                val initialOptions = combine(
                    printings,
                    cachedPrices,
                    resolution?.displayName ?: printings.first().name
                )
                cacheOptions(cardName, canonicalName, initialOptions)
                if (deliverEditionsBeforePrices) mainHandler.post { callback(initialOptions, null) }
                Log.d(TAG, "Ediciones de $canonicalName disponibles en ${SystemClock.elapsedRealtime() - startedAt} ms")

                // Edition metadata must not wait for the costly global price snapshot. Show the
                // list first, and only publish a second result if prices actually need loading.
                if (!forcePriceRefresh && cachedPrices.isNotEmpty()) {
                    if (!deliverEditionsBeforePrices) mainHandler.post { callback(initialOptions, null) }
                    return@submit
                }
                val prices = try {
                    priceProvider.prices(printingUuids.toSet(), forcePriceRefresh)
                } catch (priceError: Exception) {
                    Log.w(TAG, "No se pudieron actualizar los precios de $canonicalName", priceError)
                    if (!deliverEditionsBeforePrices) mainHandler.post { callback(initialOptions, null) }
                    return@submit
                }
                if (prices == cachedPrices) {
                    if (!deliverEditionsBeforePrices) mainHandler.post { callback(initialOptions, null) }
                    return@submit
                }
                val options = combine(
                    printings,
                    prices,
                    resolution?.displayName ?: printings.first().name
                )
                cacheOptions(cardName, canonicalName, options)
                if (Thread.currentThread().isInterrupted) return@submit
                mainHandler.post { callback(options, null) }
            } catch (error: Throwable) {
                if (Thread.currentThread().isInterrupted) return@submit
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

    fun identifyCardArtwork(
        cardName: String,
        jpeg: ByteArray,
        lockedSetCodes: Set<String> = emptySet(),
        callback: (CardIdentificationResult, Throwable?) -> Unit
    ): Future<*> = imageExecutor.submit {
        try {
            val resolution = nameResolver.cached(cardName)
            val canonicalName = resolution?.canonicalName ?: cardName
            val printings = catalog.editions(canonicalName)
            if (printings.isEmpty()) error("No se encontraron impresiones de '$cardName'")
            val options = combine(
                printings,
                dao.pricesFor(printings.map { it.uuid }),
                resolution?.displayName ?: printings.first().name
            )
            val result = artworkIdentifier.identify(jpeg, options, lockedSetCodes)
            if (!Thread.currentThread().isInterrupted) mainHandler.post { callback(result, null) }
        } catch (error: Throwable) {
            if (!Thread.currentThread().isInterrupted) {
                mainHandler.post { callback(CardIdentificationResult(emptyList(), false, 0), error) }
            }
        }
    }

    /** ManaBox-style quick mode: use the first locally known printing and skip image downloads. */
    fun quickScanCard(
        cardName: String,
        lockedSetCodes: Set<String> = emptySet(),
        callback: (CardEditionOption?, Throwable?) -> Unit
    ): Future<*> = imageExecutor.submit {
        try {
            val resolution = nameResolver.cached(cardName)
            val canonicalName = resolution?.canonicalName ?: cardName
            val normalizedName = MtgJsonCatalogDataProvider.normalize(canonicalName)
            val cached = dao.printingsByName(normalizedName)
            val locked = lockedSetCodes.mapTo(HashSet()) { it.trim().uppercase() }
            // With no set lock, quick mode must acknowledge the scan immediately. The caller can
            // persist the OCR name now and let the normal metadata request resolve it in background.
            if (cached.isEmpty() && locked.isEmpty()) {
                if (!Thread.currentThread().isInterrupted) mainHandler.post { callback(null, null) }
                return@submit
            }
            val printings = if (cached.isNotEmpty()) cached else catalog.editions(canonicalName)
            val eligible = printings.filter { locked.isEmpty() || it.setCode.uppercase() in locked }
            if (eligible.isEmpty()) error("No hay impresiones disponibles para '$cardName'")
            val prices = dao.pricesFor(eligible.map { it.uuid })
            val options = combine(
                eligible,
                prices,
                resolution?.displayName ?: eligible.first().name
            )
            val representative = options.firstOrNull { !it.isFoil } ?: options.firstOrNull()
            if (!Thread.currentThread().isInterrupted) mainHandler.post { callback(representative, null) }
        } catch (error: Throwable) {
            if (!Thread.currentThread().isInterrupted) mainHandler.post { callback(null, error) }
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
    ): Future<*> {
        return imageExecutor.submit {
            try {
                val variants = imageProvider.getImageLanguages(setCode, collectorNumber)
                mainHandler.post { callback(variants, null) }
            } catch (error: Throwable) {
                if (Thread.currentThread().isInterrupted) return@submit
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

    @Synchronized
    private fun cachedOptions(cardName: String): List<CardEditionOption>? =
        optionCache[MtgJsonCatalogDataProvider.normalize(cardName)]

    @Synchronized
    private fun cacheOptions(requestedName: String, canonicalName: String, options: List<CardEditionOption>) {
        optionCache[MtgJsonCatalogDataProvider.normalize(requestedName)] = options
        optionCache[MtgJsonCatalogDataProvider.normalize(canonicalName)] = options
    }

    companion object {
        private const val TAG = "CardRepository"
        @Volatile private var instance: CardRepository? = null
        @JvmStatic fun get(context: Context): CardRepository = instance ?: synchronized(this) {
            instance ?: CardRepository(context.applicationContext).also { instance = it }
        }
    }
}

private object CompletedFuture : Future<Unit> {
    override fun cancel(mayInterruptIfRunning: Boolean) = false
    override fun isCancelled() = false
    override fun isDone() = true
    override fun get() = Unit
    override fun get(timeout: Long, unit: TimeUnit) = Unit
}
