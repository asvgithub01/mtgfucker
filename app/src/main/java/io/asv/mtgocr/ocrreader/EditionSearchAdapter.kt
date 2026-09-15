package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.Normalizer
import java.util.Locale

internal object EditionSearch {
    fun matches(code: String, name: String, query: String): Boolean {
        val text = query.trim()
        if (text.isEmpty()) return true
        val uppercaseCode = text.any { it.isLetter() } && text == text.uppercase(Locale.ROOT)
        return if (uppercaseCode) code.contains(text, ignoreCase = true)
        else normalize(name).contains(normalize(text)) || code.equals(text, ignoreCase = true)
    }

    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)
}

/** Shared symbol/code/name popup for scanner locks and card printing searches. */
class EditionSearchAdapter(context: Context, private val source: List<Entry>) :
    ArrayAdapter<EditionSearchAdapter.Entry>(context, android.R.layout.simple_dropdown_item_1line) {
    data class Entry @JvmOverloads constructor(val code: String, val name: String, val detail: String = "",
        val imageUrl: String? = null, val printingKey: String = "") {
        override fun toString() = code
    }
    private var visible = source
    override fun getCount() = visible.size
    override fun getItem(position: Int) = visible[position]
    override fun getFilter(): Filter = searchFilter
    private val searchFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val result = source.filter { EditionSearch.matches(it.code, it.name, constraint?.toString().orEmpty()) }
            return FilterResults().apply { values = result; count = result.size }
        }
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            @Suppress("UNCHECKED_CAST")
            visible = results.values as List<Entry>
            notifyDataSetChanged()
        }
        override fun convertResultToString(resultValue: Any?) = (resultValue as Entry).code
    }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(35, 35, 35))
        }
        val item = getItem(position)
        if (item.printingKey.isNotBlank()) {
            val thumbnail = RoundedCardImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = item.detail
            }
            val density = context.resources.displayMetrics.density
            row.addView(thumbnail, LinearLayout.LayoutParams((48 * density).toInt(), (68 * density).toInt()))
            if (item.imageUrl.isNullOrBlank()) thumbnail.setImageResource(R.drawable.backmtg)
            else CardImageCache.display(context, item.imageUrl, thumbnail)
        }
        val icon = ImageView(context)
        val size = (32 * context.resources.displayMetrics.density).toInt()
        row.addView(icon, LinearLayout.LayoutParams(size, size))
        SetSymbolLoader.display(context, item.code, icon)
        row.addView(TextView(context).apply {
            text = "${item.code.uppercase(Locale.ROOT)} · ${item.name}" +
                item.detail.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
            setTextColor(Color.WHITE)
            setPadding(16, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }
}
