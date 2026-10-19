package io.asv.mtgocr.ocrreader

import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.SelectedPrices
import io.asv.mtgocr.ocrreader.data.PriceCurrency

/** Draft only: selecting a name or edition never writes inventory or photo detections. */
internal class PhotoCardPreview(private val host:AppCompatActivity,private val add:(CardEditionOption)->Unit):LinearLayout(host) {
    private var candidate:CardEditionOption?=null
    private var generation=0
    private val image=ImageView(host).apply { id=R.id.photoDraftImage;scaleType=ImageView.ScaleType.FIT_CENTER }
    private val title=CollectionPopupChrome.label(host,"")
    private val price=CollectionPopupChrome.label(host,"").apply { id=R.id.photoDraftPrice }
    private val edition=CollectionPopupChrome.button(host,R.string.editions) {
        val selected=candidate ?: return@button
        val expected=generation
        EditionPicker.showGrid(host,selected.cardName,selected.finish) { next -> if(expected==generation) show(next) }
    }.apply { id=R.id.photoDraftEdition }
    init {
        id=R.id.photoDraft;orientation=VERTICAL;visibility=View.GONE
        setPadding(16,12,16,12);setBackgroundColor(0xC0000000.toInt())
        addView(image,LayoutParams(-1,CollectionPopupChrome.dp(host,160)))
        addView(title);addView(edition);addView(price)
        val actions=LinearLayout(host)
        actions.addView(CollectionPopupChrome.button(host,R.string.photo_draft_add) { candidate?.let(add) }.apply { id=R.id.photoDraftAdd },LayoutParams(0,-2,1f))
        actions.addView(CollectionPopupChrome.button(host,R.string.photo_draft_discard) { clear() }.apply { id=R.id.photoDraftDiscard },LayoutParams(0,-2,1f))
        addView(actions)
    }
    fun clear() { generation++;candidate=null;visibility=View.GONE;image.setImageDrawable(null);price.text="" }
    fun show(option:CardEditionOption) {
        val expected=++generation;candidate=option;visibility=View.VISIBLE
        title.text=option.displayName
        edition.text="${option.setName} (${option.setCode}) #${option.collectorNumber} · ${option.finish}"
        CardImageCache.display(host,option.imageUrl.orEmpty(),image)
        price.setText(R.string.no_price)
        val card=io.asv.mtgocr.ocrreader.model.CardInfo("","","","","1").apply { printingUuid=option.printingUuid;finish=option.finish }
        fun refresh() {
            if(expected!=generation || host.isFinishing || host.isDestroyed)return
            price.text=SelectedPrices.quote(host,option.printingUuid,option.finish)?.let {
                PriceCurrency.format(host,PriceCurrency.convert(host,it.amount,it.currency),PriceCurrency.preferred(host))
            } ?: host.getString(R.string.no_price)
        }
        SelectedPrices.ensure(host,listOf(card),Runnable { refresh() });refresh()
    }
}

internal fun CardEditionOption.toPhotoCard() = PhotoScanCard(
        cardName = cardName,
        displayName = displayName,
        detectedQuantity = 1,
        printingUuid = printingUuid,
        setCode = setCode,
        setName = setName,
        collectorNumber = collectorNumber,
        finish = finish,
        imageUrl = imageUrl.orEmpty(),
        typeLine = typeLine,
        rulesText = rulesText,
        price = price,
        currency = currency ?: "EUR",
        mcmId = mcmId,
        mcmMetaId = mcmMetaId,
        mcmSetId = mcmSetId,
        mcmSetIdExtras = mcmSetIdExtras,
        mcmSetName = mcmSetName
    )
