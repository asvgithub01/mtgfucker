package io.asv.mtgocr.ocrreader

/** Resolves the artwork-only crop that Scryfall publishes alongside each card image. */
object LaunchArtworkUrl {
    private val scryfallImage = Regex(
        "^(https?://cards\\.scryfall\\.io/)(small|normal|large|png|border_crop|art_crop)(/.*)$",
        RegexOption.IGNORE_CASE
    )

    @JvmStatic
    fun resolve(imageUrl: String?): String? {
        val url = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return scryfallImage.replace(url) { match ->
            "${match.groupValues[1]}art_crop${match.groupValues[3]}"
        }
    }

    @JvmStatic
    fun isAvailable(imageUrl: String?): Boolean {
        val url = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return scryfallImage.matches(url)
    }
}
