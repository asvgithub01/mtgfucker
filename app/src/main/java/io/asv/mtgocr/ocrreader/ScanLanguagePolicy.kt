package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.CardLanguage

/** A shared title (e.g. Elfos de Llanowar) is not proof of Spanish versus Portuguese. */
internal object ScanLanguagePolicy {
    private val supported = setOf("en", "es", "fr", "de", "it", "pt", "ja", "ko", "ru", "zhs", "zht")
    /** Distinctive rules verbs also work on mana abilities too short for statistical language ID. */
    fun shortRulesLanguage(text: String): String? {
        val words = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "").lowercase(java.util.Locale.ROOT)
            .split("[^a-z]+".toRegex()).toSet()
        val signals = mapOf(
            "es" to setOf("agrega", "anade", "endereza", "gira"),
            "pt" to setOf("adicione", "desvire", "vire"),
            "en" to setOf("add", "untap")
        ).filterValues { verbs -> verbs.any { it in words } }.keys
        return signals.singleOrNull()
    }

    fun choose(fallback: String, candidates: List<Pair<String, Float>>): Pair<String, Float> {
        val ranked = candidates.filter { it.first in supported }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return fallback to 0f
        val margin = best.second - (ranked.getOrNull(1)?.second ?: 0f)
        return if (best.second >= .65f && margin >= .20f) best else fallback to 0f
    }
}

object ScanIdentity {
    @JvmStatic
    fun withName(option: CardEditionOption, name: String): CardEditionOption =
        option.copy(displayName = name.ifBlank { option.displayName })

    @JvmStatic
    fun displayName(current: String?, language: String?, incoming: String): String =
        if (current.isNullOrBlank() || CardLanguage.toCode(language) == "en") incoming else current
}
