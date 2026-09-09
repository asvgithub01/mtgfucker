package io.asv.mtgocr.ocrreader

import android.content.Context
import android.content.Intent
import io.asv.mtgocr.ocrreader.data.SetCardOption
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.model.CardInfo

/** Opens the existing full-screen card pager with the cards currently visible in a list. */
object CardGalleryLauncher {
    @JvmStatic
    fun openCards(context: Context, cards: List<CardInfo>, selectedCollectionItemId: String): Boolean {
        val items = cards.map { card ->
            CardGalleryStore.Page(
                card.collectionItemId,
                card.name.orEmpty(),
                card.collectionItemId,
                card.imgPath.orEmpty(),
                cardLabel(card.name.orEmpty(), card.setCode.orEmpty(), card.collectorNumber.orEmpty()),
                card.setCode.orEmpty(),
                card.collectorNumber.orEmpty(),
                PriceCurrency.format(context, card),
                card.finish.orEmpty()
            )
        }
        return open(context, items, selectedCollectionItemId)
    }

    @JvmStatic
    fun openSetCards(
        context: Context,
        cards: List<SetCardOption>,
        selectedPrintingUuid: String,
        ownedCollectionItemIds: Map<String, String> = emptyMap()
    ): Boolean {
        val items = cards.map { card ->
            CardGalleryStore.Page(
                card.printingUuid,
                card.cardName,
                ownedCollectionItemIds[card.printingUuid].orEmpty(),
                card.imageUrl.orEmpty(),
                cardLabel(card.cardName, card.setCode, card.collectorNumber),
                card.setCode,
                card.collectorNumber,
                card.price?.let {
                    PriceCurrency.format(context, it, card.currency.orEmpty())
                }.orEmpty(),
                card.finish
            )
        }
        return open(context, items, selectedPrintingUuid)
    }

    @JvmStatic
    fun openPhotoCards(
        context: Context,
        cards: List<PhotoScanCard>,
        selectedId: String,
        ownedCollectionItemIds: Map<String, String>
    ): Boolean {
        val items = cards.map { card ->
            CardGalleryStore.Page(
                card.id,
                card.cardName,
                ownedCollectionItemIds[card.id].orEmpty(),
                card.imageUrl,
                cardLabel(card.displayName, card.setCode, card.collectorNumber),
                card.setCode,
                card.collectorNumber,
                card.price?.let { PriceCurrency.format(context, it, card.currency) }.orEmpty(),
                card.finish
            )
        }
        return open(context, items, selectedId)
    }

    private fun open(context: Context, source: List<CardGalleryStore.Page>, selectedKey: String): Boolean {
        val pages = source.filter { it.imageUrl.isNotBlank() }
        if (pages.isEmpty()) return false
        val selectedIndex = pages.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
        val galleryToken = CardGalleryStore.put(pages)
        context.startActivity(Intent(context, CardImageActivity::class.java).apply {
            putExtra(CardImageActivity.EXTRA_GALLERY_TOKEN, galleryToken)
            putExtra(CardImageActivity.EXTRA_EDITION_INDEX, selectedIndex)
        })
        return true
    }

    private fun cardLabel(name: String, setCode: String, collectorNumber: String): String =
        buildList {
            add(name)
            if (setCode.isNotBlank()) add(setCode.uppercase())
            if (collectorNumber.isNotBlank()) add("#$collectorNumber")
        }.joinToString(" · ")
}
