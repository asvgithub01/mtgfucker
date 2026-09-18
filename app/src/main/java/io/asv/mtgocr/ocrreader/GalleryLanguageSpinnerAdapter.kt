package io.asv.mtgocr.ocrreader

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import io.asv.mtgocr.ocrreader.data.CardImageVariant

/** Keeps the selected language readable over every palette and shows its flag at a glance. */
class GalleryLanguageSpinnerAdapter(
    context: Context,
    private val variants: List<CardImageVariant>,
    private val labels: List<String>
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = variants.size

    override fun getItem(position: Int): CardImageVariant = variants[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(position, convertView, parent)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(position, convertView, parent)

    private fun bind(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.gallery_language_item, parent, false)
        view.findViewById<TextView>(R.id.txtGalleryLanguageFlag).text = flagFor(variants[position].languageCode)
        view.findViewById<TextView>(R.id.txtGalleryLanguageName).text = labels[position]
        return view
    }

    internal fun flagFor(code: String): String = when (code.lowercase()) {
        "en" -> "🇬🇧"
        "es" -> "🇪🇸"
        "fr" -> "🇫🇷"
        "de" -> "🇩🇪"
        "it" -> "🇮🇹"
        "pt" -> "🇵🇹"
        "ja" -> "🇯🇵"
        "ko" -> "🇰🇷"
        "ru" -> "🇷🇺"
        "zhs" -> "🇨🇳"
        "zht" -> "🇹🇼"
        "he" -> "🇮🇱"
        "grc" -> "🇬🇷"
        "ar" -> "🌐"
        "sa" -> "🇮🇳"
        "la" -> "🏛️"
        "phyrexian" -> "Φ"
        else -> "🌐"
    }
}
