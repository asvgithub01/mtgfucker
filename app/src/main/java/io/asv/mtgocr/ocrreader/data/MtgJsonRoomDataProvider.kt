package io.asv.mtgocr.ocrreader.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import io.asv.mtgocr.ocrreader.model.CardInfo

/** Java-facing adapter used by the unchanged OCR flow. */
class MtgJsonRoomDataProvider(
    context: Context,
    handler: Handler,
    requestKey: Int,
    private val forcePriceRefresh: Boolean
) : DataProviderBase(), IDataProvider {
    private val appContext = context.applicationContext
    private val repository = CardRepository.get(context)

    init {
        mHandler = handler
        mRequestKey = requestKey
    }

    override fun GetCardInfo(name_card: String, cardInfo: CardInfo) {
        val requestedName = Uri.decode(name_card).trim()
        // Prices participate in the provisional choice. Existing exact identities are never substituted.
        val originalPrinting = cardInfo.printingUuid.orEmpty()
        val originalFinish = cardInfo.finish.orEmpty().ifBlank { "nonfoil" }
        repository.loadCard(requestedName, forcePriceRefresh, false) { options, error ->
            if (error != null || options.isEmpty()) {
                sendMessage(ERROR, cardInfo)
                return@loadCard
            }
            val representative = if (originalPrinting.isNotBlank()) options.firstOrNull {
                it.printingUuid == originalPrinting && it.finish == originalFinish
            } else ScanPrintingPolicy.recentAffordable(options, originalFinish == "foil") {
                option -> option.price?.let { PriceCurrency.convert(appContext, it, option.currency.orEmpty()) }
            }
            if (representative == null) { sendMessage(ERROR, cardInfo); return@loadCard }
            if (originalPrinting.isBlank()) cardInfo.setProvisionalEdition(true)
            cardInfo.name = io.asv.mtgocr.ocrreader.ScanIdentity.displayName(
                cardInfo.name, cardInfo.languageCode, representative.displayName)
            cardInfo.description = listOf(representative.typeLine, representative.rulesText)
                .filter { it.isNotBlank() }.joinToString("\n")
            cardInfo.imgPath = representative.imageUrl.orEmpty()
            cardInfo.printingUuid = representative.printingUuid
            cardInfo.setCode = representative.setCode
            cardInfo.setName = representative.setName
            cardInfo.collectorNumber = representative.collectorNumber
            cardInfo.finish = representative.finish
            representative.price?.let { amount ->
                val display = "%.2f %s".format(amount, representative.currency.orEmpty())
                cardInfo.price = display
                cardInfo.priceL = amount.toString()
                cardInfo.priceM = amount.toString()
                cardInfo.priceH = amount.toString()
            }
            repository.initializePrinting(cardInfo.collectionItemId, representative) { accepted ->
                sendMessage(if (accepted) ALL_DATA_COMPLETE else ERROR, cardInfo)
            }
        }
    }
}
