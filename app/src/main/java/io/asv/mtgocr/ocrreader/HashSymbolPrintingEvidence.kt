package io.asv.mtgocr.ocrreader

import java.util.Locale

/** V2's symbol must not be vetoed by ordinary rules words or power/toughness in footer OCR.
 * Only a short, explicit SET LANGUAGE footer row authorizes printing-token filters.
 * The broad parser stays unchanged for other scanners; title/year evidence is retained.
 */
internal object HashSymbolPrintingEvidence {
    private val languages = mapOf("EN" to "en", "ES" to "es", "SP" to "es", "FR" to "fr",
        "DE" to "de", "IT" to "it", "PT" to "pt", "JA" to "ja", "JP" to "ja",
        "KO" to "ko", "KR" to "ko", "RU" to "ru", "ZHS" to "zhs", "ZHT" to "zht",
        "CS" to "zhs", "CT" to "zht")
    private val footer = Regex("^([A-Z0-9]{2,6})\\s*[•·]?\\s+([A-Z]{2,3})(?:\\s+[A-Z])?$")
    private val collector = Regex("^(?:[CURMSL]\\s*)?(\\d{1,4}[A-Z]?)(?:\\s*/\\s*\\d{2,4})?(?:\\s+[CURMSL])?$")

    fun trusted(guess: PrintingMetadataGuess?, knownSets: Set<String>): PrintingMetadataGuess? {
        if (guess == null) return null
        val codes = knownSets.mapTo(HashSet()) { it.uppercase(Locale.ROOT) }
        val lines = guess.rawText.uppercase(Locale.ROOT).lines().map(String::trim)
        val rows = lines.mapIndexedNotNull { i, line ->
            val match = footer.matchEntire(line) ?: return@mapIndexedNotNull null
            val set = match.groupValues[1].takeIf { it in codes } ?: return@mapIndexedNotNull null
            val language = languages[match.groupValues[2]] ?: return@mapIndexedNotNull null
            Triple(i, set, language)
        }
        val unambiguous = rows.map { it.second to it.third }.distinct().singleOrNull()
        if (unambiguous == null) return guess.copy(setCode = null, collectorNumber = null,
            languageCode = null, setCodeCandidates = emptyList())
        val numbers = rows.mapNotNull { (i, _, _) ->
            lines.getOrNull(i - 1)?.let { collector.matchEntire(it)?.groupValues?.get(1) }
        }.distinct()
        return guess.copy(setCode = unambiguous.first, languageCode = unambiguous.second,
            collectorNumber = numbers.singleOrNull(), setCodeCandidates = listOf(unambiguous.first))
    }
}
