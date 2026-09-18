package io.asv.mtgocr.ocrreader

import android.content.Context
import androidx.annotation.StringRes

data class DeckFormatRule(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val summaryRes: Int,
    val minimumMain: Int,
    val maximumSideboard: Int,
    val maximumCopies: Int
) {
    fun label(context: Context): String = context.getString(labelRes)
    fun summary(context: Context): String = context.getString(summaryRes)
}

object DeckFormatRules {
    val all: List<DeckFormatRule> = listOf(
        DeckFormatRule("standard", R.string.deck_format_standard, R.string.deck_format_standard_rules, 60, 15, 4),
        DeckFormatRule("modern", R.string.deck_format_modern, R.string.deck_format_modern_rules, 60, 15, 4),
        DeckFormatRule("pioneer", R.string.deck_format_pioneer, R.string.deck_format_pioneer_rules, 60, 15, 4),
        DeckFormatRule("pauper", R.string.deck_format_pauper, R.string.deck_format_pauper_rules, 60, 15, 4),
        DeckFormatRule("legacy", R.string.deck_format_legacy, R.string.deck_format_legacy_rules, 60, 15, 4),
        DeckFormatRule("vintage", R.string.deck_format_vintage, R.string.deck_format_vintage_rules, 60, 15, 4),
        DeckFormatRule("commander", R.string.deck_format_commander, R.string.deck_format_commander_rules, 100, 0, 1),
        DeckFormatRule("free", R.string.deck_format_free, R.string.deck_format_free_rules, 0, Int.MAX_VALUE, Int.MAX_VALUE)
    )

    @JvmStatic fun byId(id: String?): DeckFormatRule = all.firstOrNull { it.id == id } ?: all.last()
}
