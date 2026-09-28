package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.app.AlertDialog
import io.asv.mtgocr.ocrreader.model.CardInfo
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import com.bumptech.glide.Glide

/** Single-row recent inventory, keyed by collectionItemId, not an async list position. */
class RecentScansView(context: Context, attrs: android.util.AttributeSet? = null) : RecyclerView(context, attrs) {
    fun interface Listener { fun onCard(card: CardInfo) }
    private val pending = linkedSetOf<String>()
    fun beginPending(key: String) { if (pending.add(key)) { strip.notifyDataSetChanged(); scrollToPosition(0) } }
    fun endPending(key: String) { previews.remove(key); if (pending.remove(key)) strip.notifyDataSetChanged() }
    private val previews = linkedMapOf<String, android.graphics.Bitmap>()
    fun setPendingPreview(key: String, bitmap: android.graphics.Bitmap) {
        if (key !in pending) return
        previews[key] = bitmap
        strip.notifyDataSetChanged()
    }
    fun completePending(key: String, itemId: String?, notify: Boolean = true) {
        val preview = previews.remove(key)
        if (preview != null && itemId != null) previews[itemId] = preview
        while (previews.size > 30) previews.remove(previews.keys.first())
        pending.remove(key)
        if (notify) strip.notifyDataSetChanged()
    }
    private var cards = emptyList<CardInfo>()
    private var listener: Listener? = null
    private val strip = object : Adapter<Holder>() {
        override fun getItemCount() = pending.size + cards.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LayoutParams((80*resources.displayMetrics.density).toInt(), -1)
                setPadding(4,4,4,4)
            }
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val price = TextView(context).apply { setTextColor(Color.WHITE); textSize=11f; gravity=17; maxLines=1 }
            row.addView(image, LinearLayout.LayoutParams(-1,0,1f)); row.addView(price)
            return Holder(row,image,price)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val identity = if (position < pending.size) pending.toList().asReversed()[position] else cards[position - pending.size].collectionItemId
            if (holder.identity != identity) {
                Glide.clear(holder.image)
                holder.image.setImageDrawable(null)
                holder.image.setTag(R.id.card_image_cache_url, null)
                holder.identity = identity
            }
            if (position < pending.size) {
                val preview = previews[pending.toList().asReversed()[position]]
                if (preview == null) CardImageCache.showLoading(holder.image) else {
                    Glide.clear(holder.image)
                    holder.image.setTag(R.id.card_image_cache_url, null)
                    holder.image.setImageBitmap(preview)
                }
                holder.price.text = "…"
                holder.itemView.contentDescription = context.getString(R.string.review_pending)
                holder.itemView.setOnClickListener(null)
                return
            }
            val card = cards[position - pending.size]
            val preview = previews[card.collectionItemId]?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
            if (card.imgPath.isNullOrBlank()) {
                CardImageCache.showLoading(holder.image)
                if (preview != null) holder.image.setImageDrawable(preview)
            } else CardImageCache.displayWithPlaceholder(context, card.imgPath, holder.image, preview)
            holder.price.text = "${card.quantityCount}× · ${PriceCurrency.format(context, card)}"
            holder.itemView.contentDescription = "${card.name}, ${holder.price.text}, ${card.finish.orEmpty()}"
            holder.itemView.setOnClickListener { listener?.onCard(card) }
        }
        override fun onViewRecycled(holder: Holder) { Glide.clear(holder.image); holder.image.setImageDrawable(null); holder.image.setTag(R.id.card_image_cache_url,null) }
    }
    init { itemAnimator = null; layoutManager=LinearLayoutManager(context,HORIZONTAL,false); adapter=strip; setBackgroundColor(0xAA000000.toInt()) }
    fun submit(items: List<CardInfo>, onCard: Listener) {
        val first = cards.firstOrNull()?.collectionItemId
        cards = items.asReversed().distinctBy { it.collectionItemId }.take(30).map { it.snapshotForPersistence() }
        listener=onCard; strip.notifyDataSetChanged()
        if (cards.firstOrNull()?.collectionItemId != first) scrollToPosition(0)
    }
    private class Holder(row: LinearLayout,val image: ImageView,val price: TextView): ViewHolder(row) { var identity: String? = null }
}

object RecentScanActions {
    fun interface Action { fun run(index: Int) }
    @JvmStatic fun show(context: Context, card: CardInfo, action: Action) {
        val labels = arrayOf(context.getString(R.string.recent_add), context.getString(R.string.recent_remove),
            context.getString(if (CardFinish.isFoil(card.finish)) R.string.nonfoil else R.string.foil),
            context.getString(R.string.recent_delete), context.getString(R.string.view_card_details))
        AlertDialog.Builder(context).setTitle(card.name).setItems(labels) { _, index -> action.run(index) }.show()
    }
}
