package io.asv.mtgocr.ocrreader

/** Shared flags for the gallery selector and scan review. */
object CardLanguageFlag {
    fun flagFor(code: String): String = when (code.trim().lowercase(java.util.Locale.ROOT)) {
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
    fun label(code: String): String = "${flagFor(code)} ${code.trim().uppercase(java.util.Locale.ROOT)}"
}
