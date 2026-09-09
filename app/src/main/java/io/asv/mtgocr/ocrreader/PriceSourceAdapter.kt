package io.asv.mtgocr.ocrreader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.asv.mtgocr.ocrreader.data.PriceSourceDefinition
import java.util.Collections

class PriceSourceAdapter(
    sources: List<PriceSourceDefinition>,
    private val onOrderChanged: (List<PriceSourceDefinition>) -> Unit
) : RecyclerView.Adapter<PriceSourceAdapter.Holder>() {
    private val items = sources.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.price_source_item, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.priority.text = (position + 1).toString()
        holder.name.text = item.label
        holder.description.text = item.description
    }

    override fun getItemCount() = items.size

    fun move(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        Collections.swap(items, from, to)
        notifyItemMoved(from, to)
        notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(from - to) + 1)
        onOrderChanged(items.toList())
        return true
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val priority: TextView = view.findViewById(R.id.txtPriceSourcePriority)
        val name: TextView = view.findViewById(R.id.txtPriceSourceName)
        val description: TextView = view.findViewById(R.id.txtPriceSourceDescription)
    }
}
