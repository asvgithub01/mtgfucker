package io.asv.mtgocr.ocrreader.data

import android.content.Context
import androidx.annotation.StringRes
import io.asv.mtgocr.ocrreader.R

data class PriceSourceDefinition(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int
) {
    fun label(context: Context): String = context.getString(labelRes)
    fun description(context: Context): String = context.getString(descriptionRes)
}

/** User-controlled priority for the price providers used by the repository. */
object PriceSourcePreferences {
    const val CARDMARKET = "cardmarket"
    const val SCRYFALL = "scryfall"
    const val TCGPLAYER = "tcgplayer"
    const val CARDKINGDOM = "cardkingdom"
    const val CARDSPHERE = "cardsphere"

    private const val PREFERENCES = "price_source_preferences"
    private const val KEY_ORDER = "price_source_order"

    @JvmField
    val available = listOf(
        PriceSourceDefinition(CARDMARKET, R.string.price_source_cardmarket, R.string.price_source_cardmarket_description),
        PriceSourceDefinition(SCRYFALL, R.string.price_source_scryfall, R.string.price_source_scryfall_description),
        PriceSourceDefinition(TCGPLAYER, R.string.price_source_tcgplayer, R.string.price_source_tcgplayer_description),
        PriceSourceDefinition(CARDKINGDOM, R.string.price_source_cardkingdom, R.string.price_source_cardkingdom_description),
        PriceSourceDefinition(CARDSPHERE, R.string.price_source_cardsphere, R.string.price_source_cardsphere_description)
    )

    @JvmStatic
    fun load(context: Context): List<PriceSourceDefinition> {
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, null)
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
        val byId = available.associateBy { it.id }
        return buildList {
            saved.mapNotNullTo(this) { byId[it] }
            available.filterTo(this) { source -> none { it.id == source.id } }
        }
    }

    @JvmStatic
    fun priorityIds(context: Context): List<String> = load(context).map { it.id }

    @JvmStatic
    fun save(context: Context, sources: List<PriceSourceDefinition>) {
        val valid = sources.map { it.id }.filter { id -> available.any { it.id == id } }.distinct()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, valid.joinToString(","))
            .apply()
    }
}
