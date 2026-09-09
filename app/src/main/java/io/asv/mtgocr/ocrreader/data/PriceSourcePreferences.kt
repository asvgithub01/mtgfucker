package io.asv.mtgocr.ocrreader.data

import android.content.Context

data class PriceSourceDefinition(
    val id: String,
    val label: String,
    val description: String
)

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
        PriceSourceDefinition(CARDMARKET, "Cardmarket", "EUR · precio retail diario vía MTGJSON"),
        PriceSourceDefinition(SCRYFALL, "Scryfall", "EUR · impresión exacta, sin cuenta"),
        PriceSourceDefinition(TCGPLAYER, "TCGplayer", "USD · precio retail diario vía MTGJSON"),
        PriceSourceDefinition(CARDKINGDOM, "Card Kingdom", "USD · precio retail diario vía MTGJSON"),
        PriceSourceDefinition(CARDSPHERE, "Cardsphere", "USD · índice diario vía MTGJSON")
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
