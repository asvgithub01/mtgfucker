package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import io.asv.mtgocr.ocrreader.data.*
import io.asv.mtgocr.ocrreader.model.CardInfo

object PriceSourcePresentation {
    @JvmStatic fun label(context: Context, provider: String): String =
        PriceSourcePreferences.available.firstOrNull { it.id==provider }?.label(context) ?: provider
    @JvmStatic fun format(context: Context, card: CardInfo): CharSequence {
        val price=PriceCurrency.format(context,card)
        return append(context,price,SelectedPrices.quote(context,card)?.provider)
    }
    @JvmStatic fun append(context: Context, price: String, provider: String?): CharSequence {
        if(price.isBlank() || provider.isNullOrBlank())return price
        val short=when(provider) { "cardmarket" -> "MKM";"scryfall" -> "Scryfall";"tcgplayer" -> "TCG";"cardkingdom" -> "CK";"cardsphere" -> "CS";else -> provider }
        return SpannableString("$price · $short").apply {
            setSpan(RelativeSizeSpan(.7f),price.length,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(if(provider==PriceSourcePreferences.selectedId(context)) Color.LTGRAY else Color.rgb(255,210,128)),price.length,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
