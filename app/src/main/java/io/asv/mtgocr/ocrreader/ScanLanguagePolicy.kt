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
            "it" to setOf(
                "aggiungi", "pesca", "bersaglio", "avversario", "segnalino",
                "distruggi", "sacrifica", "infligge", "cimitero", "battaglia"
            ),
            "en" to setOf("add", "untap")
        ).filterValues { verbs -> verbs.any { it in words } }.keys
        return signals.singleOrNull()
    }

    /** Strong title clues are useful when the rules OCR is too short or badly classified. */
    fun localizedTitleLanguage(text: String): String? {
        val words = normalizedWords(text)
        val signals = mapOf(
            "it" to setOf(
                "cavallo", "cavaliere", "strega", "delle", "degli", "dalla", "dello"
            ),
            "es" to setOf("caballo", "caballero", "bruja"),
            "pt" to setOf("cavalo", "cavaleiro", "bruxa")
        ).filterValues { clues -> clues.any { it in words } }.keys
        return signals.singleOrNull()
    }

    /** ML Kit can occasionally label noisy Latin OCR as Chinese; reject impossible scripts. */
    fun scriptCompatibleCandidates(
        text: String,
        candidates: List<Pair<String, Float>>
    ): List<Pair<String, Float>> {
        val hasHan = text.any { it.code in 0x3400..0x9FFF }
        val hasKana = text.any { it.code in 0x3040..0x30FF }
        val hasHangul = text.any { it.code in 0xAC00..0xD7AF }
        val hasCyrillic = text.any { it.code in 0x0400..0x052F }
        return candidates.filter { (language, _) ->
            when (language) {
                "zhs", "zht" -> hasHan
                "ja" -> hasHan || hasKana
                "ko" -> hasHangul
                "ru" -> hasCyrillic
                else -> true
            }
        }
    }

    fun choose(fallback: String, candidates: List<Pair<String, Float>>): Pair<String, Float> {
        val ranked = candidates.filter { it.first in supported }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return fallback to 0f
        val margin = best.second - (ranked.getOrNull(1)?.second ?: 0f)
        return if (best.second >= .65f && margin >= .20f) best else fallback to 0f
    }

    private fun normalizedWords(text: String): Set<String> =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(java.util.Locale.ROOT)
            .split("[^a-z]+".toRegex())
            .filter(String::isNotBlank)
            .toSet()
}

object ScanIdentity {
    @JvmStatic
    fun withName(option: CardEditionOption, name: String): CardEditionOption =
        option.copy(displayName = name.ifBlank { option.displayName })

    @JvmStatic
    fun displayName(current: String?, language: String?, incoming: String): String =
        if (current.isNullOrBlank() || CardLanguage.toCode(language) == "en") incoming else current
}
