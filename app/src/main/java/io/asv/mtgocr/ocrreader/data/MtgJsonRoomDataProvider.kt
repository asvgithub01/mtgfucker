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
    private val repository = CardRepository.get(context)

    init {
        mHandler = handler
        mRequestKey = requestKey
    }

    override fun GetCardInfo(name_card: String, cardInfo: CardInfo) {
        val requestedName = Uri.decode(name_card).trim()
        repository.loadCard(requestedName, forcePriceRefresh) { options, error ->
            if (error != null || options.isEmpty()) {
                sendMessage(ERROR, cardInfo)
                return@loadCard
            }
            val representative = options.firstOrNull { !it.isFoil } ?: options.first()
            cardInfo.name = representative.displayName
            cardInfo.description = listOf(representative.typeLine, representative.rulesText)
                .filter { it.isNotBlank() }.joinToString("\n")
            cardInfo.imgPath = representative.imageUrl.orEmpty()
            cardInfo.printingUuid = representative.printingUuid
            cardInfo.setCode = representative.setCode
            cardInfo.setName = representative.setName
            cardInfo.finish = representative.finish
            representative.price?.let { amount ->
                val display = "%.2f %s".format(amount, representative.currency.orEmpty())
                cardInfo.price = display
                cardInfo.priceL = amount.toString()
                cardInfo.priceM = amount.toString()
                cardInfo.priceH = amount.toString()
            }
            repository.selectPrinting(
                cardInfo.collectionItemId,
                representative.cardName,
                representative.printingUuid,
                representative.finish
            ) {
                sendMessage(ALL_DATA_COMPLETE, cardInfo)
            }
        }
    }
}
