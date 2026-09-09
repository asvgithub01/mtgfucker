package io.asv.mtgocr.ocrreader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class PhotoScanLibraryAdapter(
    private val onOpen: (PhotoScanEntry) -> Unit,
    private val onDelete: (PhotoScanEntry) -> Unit
) : RecyclerView.Adapter<PhotoScanLibraryAdapter.Holder>() {
    private var items: List<PhotoScanEntry> = emptyList()

    fun submit(entries: List<PhotoScanEntry>) {
        items = entries.sortedByDescending { it.createdAt }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.photo_scan_item, parent, false)
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.bind(item, onOpen, onDelete)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.imgPhotoScan)
        private val total: TextView = view.findViewById(R.id.txtPhotoScanTotal)
        private val summary: TextView = view.findViewById(R.id.txtPhotoScanSummary)
        private val state: TextView = view.findViewById(R.id.txtPhotoScanState)
        private val delete: ImageButton = view.findViewById(R.id.btnDeletePhotoScan)

        fun bind(entry: PhotoScanEntry, open: (PhotoScanEntry) -> Unit, remove: (PhotoScanEntry) -> Unit) {
            Glide.with(itemView.context).load(File(entry.imagePath)).dontAnimate().centerCrop().into(image)
            val currency = entry.cards.firstOrNull()?.currency?.ifBlank { "EUR" } ?: "EUR"
            total.text = runCatching {
                NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                    this.currency = Currency.getInstance(currency)
                }.format(entry.totalPrice())
            }.getOrElse { "%.2f %s".format(entry.totalPrice(), currency) }
            summary.text = entry.cards.joinToString(" · ") {
                "${it.detectedQuantity}× ${it.displayName}"
            }.ifBlank { itemView.context.getString(R.string.photo_no_cards_yet) }
            state.text = when (entry.state) {
                PhotoScanEntry.STATE_ANALYZING -> itemView.context.getString(R.string.photo_analyzing)
                PhotoScanEntry.STATE_ERROR -> entry.error.ifBlank {
                    itemView.context.getString(R.string.photo_analysis_error)
                }
                else -> itemView.context.resources.getQuantityString(
                    R.plurals.photo_cards_detected,
                    entry.cards.sumOf { it.detectedQuantity },
                    entry.cards.sumOf { it.detectedQuantity }
                )
            }
            itemView.setOnClickListener { open(entry) }
            image.setOnClickListener { open(entry) }
            delete.setOnClickListener { remove(entry) }
        }
    }
}
