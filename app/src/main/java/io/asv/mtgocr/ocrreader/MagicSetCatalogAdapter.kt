package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.asv.mtgocr.ocrreader.data.MagicSetOption
import java.text.Normalizer
import java.util.Locale

internal object MagicSetCatalogOrder {
    fun filterAndSort(
        source: List<MagicSetOption>,
        favorites: Set<String>,
        query: String,
        ownedSetCodes: Set<String> = emptySet(),
        onlyOwned: Boolean = false
    ): List<MagicSetOption> {
        val normalizedQuery = normalize(query)
        val normalizedOwned = ownedSetCodes.mapTo(HashSet()) { it.uppercase(Locale.ROOT) }
        return source.asSequence()
            .filter { !onlyOwned || it.code.uppercase(Locale.ROOT) in normalizedOwned }
            .filter {
                normalizedQuery.isBlank() ||
                    normalize("${it.name} ${it.code} ${it.type}").contains(normalizedQuery)
            }
            .sortedWith(
                compareByDescending<MagicSetOption> { it.code in favorites }
                    .thenBy { it.releaseDate }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            .toList()
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .trim()
}

object FavoriteSetStore {
    private const val PREFERENCES = "set_catalog_preferences"
    private const val KEY_FAVORITES = "favorite_set_codes"

    fun codes(context: Context): Set<String> = context
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getStringSet(KEY_FAVORITES, emptySet())
        .orEmpty().toSet()

    fun toggle(context: Context, code: String): Boolean {
        val updated = codes(context).toMutableSet()
        val favorite = if (!updated.add(code)) {
            updated.remove(code)
            false
        } else true
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_FAVORITES, updated).apply()
        return favorite
    }
}

class MagicSetCatalogAdapter(
    private val context: Context,
    private val onOpen: (MagicSetOption) -> Unit
) : RecyclerView.Adapter<MagicSetCatalogAdapter.Holder>() {
    private var source: List<MagicSetOption> = emptyList()
    private var visible: List<MagicSetOption> = emptyList()
    private var query = ""
    private var favorites = FavoriteSetStore.codes(context)
    private var ownedSetCodes: Set<String> = emptySet()
    private var ownedSetCounts: Map<String, Int> = emptyMap()
    private var onlyOwned = false

    fun submit(items: List<MagicSetOption>) {
        source = items
        applyFilter()
    }

    fun filter(value: String) {
        query = value
        applyFilter()
    }

    fun setOwnedSets(counts: Map<String, Int>, showOnlyOwned: Boolean) {
        ownedSetCounts = counts.mapKeys { (code, _) -> code.uppercase(Locale.ROOT) }
        ownedSetCodes = ownedSetCounts.keys
        onlyOwned = showOnlyOwned
        applyFilter()
    }

    fun visibleCount(): Int = visible.size

    private fun applyFilter() {
        favorites = FavoriteSetStore.codes(context)
        visible = MagicSetCatalogOrder.filterAndSort(
            source, favorites, query, ownedSetCodes, onlyOwned
        )
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.set_catalog_item, parent, false),
        Typeface.createFromAsset(parent.context.assets, "title_font.ttf")
    )

    override fun getItemCount(): Int = visible.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = visible[position]
        holder.bind(
            item,
            item.code in favorites,
            ownedSetCounts[item.code.uppercase(Locale.ROOT)] ?: 0,
            onOpen
        ) {
            FavoriteSetStore.toggle(context, item.code)
            applyFilter()
        }
    }

    class Holder(view: android.view.View, titleTypeface: Typeface) : RecyclerView.ViewHolder(view) {
        private val code: TextView = view.findViewById<TextView>(R.id.txtCatalogSetCode).also { it.typeface = titleTypeface }
        private val name: TextView = view.findViewById<TextView>(R.id.txtCatalogSetName).also { it.typeface = titleTypeface }
        private val metadata: TextView = view.findViewById(R.id.txtCatalogSetMetadata)
        private val symbol: ImageView = view.findViewById(R.id.imgCatalogSetSymbol)
        private val favorite: ImageButton = view.findViewById(R.id.btnFavoriteSet)

        fun bind(
            item: MagicSetOption,
            isFavorite: Boolean,
            ownedCount: Int,
            open: (MagicSetOption) -> Unit,
            toggle: () -> Unit
        ) {
            code.text = item.code
            name.text = item.name
            SetSymbolLoader.display(itemView.context, item.code, symbol)
            val catalogMetadata = itemView.context.getString(
                R.string.set_catalog_metadata, item.releaseDate, item.type, item.cardCount
            )
            metadata.text = "$catalogMetadata · ${itemView.context.getString(
                R.string.set_catalog_owned_progress,
                ownedCount,
                item.cardCount
            )}"
            favorite.setImageResource(if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            favorite.setColorFilter(
                if (isFavorite) MagicPalette.secondaryColor(itemView.context)
                else MagicPalette.primaryColor(itemView.context)
            )
            favorite.contentDescription = itemView.context.getString(
                if (isFavorite) R.string.remove_set_favorite else R.string.add_set_favorite,
                item.name
            )
            itemView.setOnClickListener { open(item) }
            favorite.setOnClickListener { toggle() }
        }
    }
}
