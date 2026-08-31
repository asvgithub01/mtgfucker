package io.asv.mtgocr.ocrreader.data

import android.content.Context
import io.asv.mtgocr.ocrreader.DataUtils
import io.asv.mtgocr.ocrreader.OcrCaptureActivity
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.util.Locale

object LegacyCollectionStore {
    private const val FILE_NAME = "myBiblio.Json"

    fun add(context: Context, options: List<SetCardOption>): List<CardInfo> {
        val collection = DataUtils.readSerializable<Biblio>(context, FILE_NAME)
            ?: Biblio(FILE_NAME, "Mis Cartukis")
        val baseTime = System.currentTimeMillis()
        val added = options.mapIndexed { index, option ->
            val priceText = option.price?.let { "%.2f %s".format(Locale.US, it, option.currency.orEmpty()) }.orEmpty()
            CardInfo(
                option.cardName,
                priceText,
                listOf(option.typeLine, option.rulesText).filter { it.isNotBlank() }.joinToString("\n"),
                option.imageUrl.orEmpty(),
                ""
            ).also { card ->
                card.addedAt = baseTime + index
                card.printingUuid = option.printingUuid
                card.setCode = option.setCode
                card.setName = option.setName
                card.collectorNumber = option.collectorNumber
                card.finish = option.finish
                option.price?.let {
                    card.priceL = it.toString()
                    card.priceM = it.toString()
                    card.priceH = it.toString()
                }
                collection.addCard(card)
            }
        }
        DataUtils.saveSerializable(context, collection, collection.nameFile)
        OcrCaptureActivity.mBiblio = collection
        return added
    }

    /** Adds one physical copy, folding it into the matching name/printing/finish row. */
    fun addCopy(context: Context, option: CardEditionOption): CardInfo {
        val collection = DataUtils.readSerializable<Biblio>(context, FILE_NAME)
            ?: Biblio(FILE_NAME, "Mis Cartukis")
        val result = addCopyToCollection(collection, option)
        val expectedQuantity = result.quantityCount
        DataUtils.saveSerializable(context, collection, collection.nameFile)

        // Re-read the file before reporting success. This also makes the object exposed to the
        // still-running collection Activity exactly the same object that will survive a restart.
        val persisted = DataUtils.readSerializable<Biblio>(context, collection.nameFile)
            ?: throw IllegalStateException("No se pudo guardar la copia en la colección")
        OcrCaptureActivity.mBiblio = persisted
        val saved = persisted.cards.firstOrNull { it.collectionItemId == result.collectionItemId }
            ?: throw IllegalStateException("La copia guardada no se pudo verificar")
        if (saved.quantityCount < expectedQuantity) {
            throw IllegalStateException("La cantidad guardada no se pudo verificar")
        }
        return saved
    }

    internal fun addCopyToCollection(collection: Biblio, option: CardEditionOption): CardInfo {
        val existing = collection.cards.firstOrNull { card -> samePrinting(card, option) }
        val result = existing?.also { it.quantityCount = it.quantityCount + 1 } ?: CardInfo(
            option.cardName,
            option.price?.let { "%.2f %s".format(Locale.US, it, option.currency.orEmpty()) }.orEmpty(),
            listOf(option.typeLine, option.rulesText).filter { it.isNotBlank() }.joinToString("\n"),
            option.imageUrl.orEmpty(),
            "1"
        ).also { card ->
            card.printingUuid = option.printingUuid
            card.setCode = option.setCode
            card.setName = option.setName
            card.collectorNumber = option.collectorNumber
            card.finish = option.finish
            option.price?.let {
                card.priceL = it.toString()
                card.priceM = it.toString()
                card.priceH = it.toString()
            }
            collection.addCard(card)
        }
        return result
    }

    /** MTGJSON calls a regular finish `nonfoil`; old collection files may call it `normal`. */
    internal fun samePrinting(card: CardInfo, option: CardEditionOption): Boolean =
        card.printingUuid.orEmpty() == option.printingUuid &&
            normalizeFinish(card.finish) == normalizeFinish(option.finish)

    private fun normalizeFinish(value: String?): String = when (value.orEmpty().trim().lowercase(Locale.ROOT)) {
        "", "normal", "regular", "non-foil" -> "nonfoil"
        else -> value.orEmpty().trim().lowercase(Locale.ROOT)
    }

    data class CopyRemovalResult(val remainingQuantity: Int, val removedCollectionItemId: String)

    /** Removes one physical copy of an exact printing/finish, deleting its row at zero. */
    fun removeCopy(context: Context, option: CardEditionOption): CopyRemovalResult? {
        val collection = DataUtils.readSerializable<Biblio>(context, FILE_NAME)
            ?: OcrCaptureActivity.mBiblio
            ?: return null
        val card = collection.cards.firstOrNull { samePrinting(it, option) } ?: return null
        val remaining = card.quantityCount - 1
        if (remaining > 0) card.quantityCount = remaining else collection.cards.remove(card)
        DataUtils.saveSerializable(context, collection, collection.nameFile)
        OcrCaptureActivity.mBiblio = collection
        return CopyRemovalResult(remaining.coerceAtLeast(0), card.collectionItemId)
    }

    fun updateSelectedEdition(
        context: Context,
        collectionItemId: String,
        option: CardEditionOption
    ): Boolean {
        val collection = DataUtils.readSerializable<Biblio>(context, FILE_NAME)
            ?: OcrCaptureActivity.mBiblio
            ?: return false
        val card = collection.cards.firstOrNull { it.collectionItemId == collectionItemId } ?: return false
        card.printingUuid = option.printingUuid
        card.setCode = option.setCode
        card.setName = option.setName
        card.collectorNumber = option.collectorNumber
        card.finish = option.finish
        card.imgPath = option.imageUrl.orEmpty()
        option.price?.let { amount ->
            val raw = amount.toString()
            card.price = "%.2f %s".format(Locale.US, amount, option.currency.orEmpty()).trim()
            card.priceL = raw
            card.priceM = raw
            card.priceH = raw
        } ?: run {
            card.price = ""
            card.priceL = ""
            card.priceM = ""
            card.priceH = ""
        }
        DataUtils.saveSerializable(context, collection, collection.nameFile)
        OcrCaptureActivity.mBiblio = collection
        return true
    }
}
