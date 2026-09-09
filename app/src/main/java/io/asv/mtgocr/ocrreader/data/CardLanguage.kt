package io.asv.mtgocr.ocrreader.data

import java.text.Normalizer
import java.util.Locale

/** Normalizes MTGJSON language names to the codes used by Scryfall image variants. */
object CardLanguage {
    @JvmStatic
    fun toCode(value: String?): String {
        val normalized = Normalizer.normalize(value.orEmpty().trim(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .replace('_', '-')
        return when (normalized) {
            "", "unknown" -> ""
            "en", "eng", "english" -> "en"
            "es", "spa", "spanish" -> "es"
            "fr", "fra", "fre", "french" -> "fr"
            "de", "deu", "ger", "german" -> "de"
            "it", "ita", "italian" -> "it"
            "pt", "por", "portuguese", "portuguese (brazil)", "brazilian portuguese" -> "pt"
            "ja", "jpn", "japanese" -> "ja"
            "ko", "kor", "korean" -> "ko"
            "ru", "rus", "russian" -> "ru"
            "zhs", "chinese simplified", "simplified chinese" -> "zhs"
            "zht", "chinese traditional", "traditional chinese" -> "zht"
            "he", "heb", "hebrew" -> "he"
            "la", "lat", "latin" -> "la"
            "grc", "ancient greek" -> "grc"
            "ar", "ara", "arabic" -> "ar"
            "sa", "san", "sanskrit" -> "sa"
            "phyrexian" -> "phyrexian"
            else -> normalized.takeIf { it.matches("[a-z]{2,3}".toRegex()) }.orEmpty()
        }
    }
}
