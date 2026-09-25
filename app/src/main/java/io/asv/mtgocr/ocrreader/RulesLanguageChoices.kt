package io.asv.mtgocr.ocrreader

/** A manual catalogue choice is not evidence of the physical card's language. */
internal object RulesLanguageChoices {
    private val all = listOf("en", "es", "pt", "fr", "de", "it", "ja", "ko", "ru", "zhs", "zht",
        "he", "la", "grc", "ar", "sa", "phyrexian")
    fun codes(available: List<String>, unavailable: Boolean): List<String> =
        if (unavailable) all else available.filter { it.isNotBlank() }.distinct()
}
