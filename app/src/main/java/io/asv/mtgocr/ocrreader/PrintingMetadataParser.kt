package io.asv.mtgocr.ocrreader

import java.text.Normalizer
import java.util.Locale

data class PrintingMetadataGuess(
    val rawText: String,
    val collectorNumber: String?,
    val setCode: String?,
    val languageCode: String?,
    val setCodeCandidates: List<String>
)

/** Extracts the stable printing tokens from OCR of a rectified card's lower band. */
object PrintingMetadataParser {
    private val collectorWithTotal = Regex(
        "(?i)(?<![0-9])([0-9]{1,4}[a-z]?)\\s*[/|]\\s*[0-9]{1,4}[a-z]?(?![0-9])"
    )
    private val labelledCollector = Regex(
        "(?i)(?:#|N[O0]\\.?\\s*)([0-9]{1,4}[a-z]?)(?![0-9])"
    )
    private val standaloneCollector = Regex("(?i)(?<![0-9])([0-9]{1,4}[a-z]?)(?![0-9])")
    private val tokenPattern = Regex("[A-Z][A-Z0-9]{1,5}")
    private val languageTokens = mapOf(
        "EN" to "en",
        "ES" to "es",
        "FR" to "fr",
        "DE" to "de",
        "IT" to "it",
        "PT" to "pt",
        "JP" to "ja",
        "JA" to "ja",
        "KR" to "ko",
        "KO" to "ko",
        "RU" to "ru",
        "CS" to "zhs",
        "CT" to "zht",
        "ZHS" to "zhs",
        "ZHT" to "zht"
    )
    private val ignoredSetTokens = setOf(
        "ART", "ARTIST", "ILLUS", "ILLUST", "MAGIC", "WIZARDS", "COAST",
        "CARD", "CARDS", "TOKEN", "DECK", "THE", "BY", "TM", "AND",
        "COMMON", "UNCOMMON", "RARE", "MYTHIC", "SPECIAL", "LAND",
        "C", "U", "R", "M", "S", "L", "T", "P"
    )

    fun parse(rawText: String, knownSetCodes: Set<String> = emptySet()): PrintingMetadataGuess {
        val normalized = normalize(rawText)
        val lines = normalized.lines().map(String::trim).filter(String::isNotBlank)
        val collector = collectorWithTotal.find(normalized)?.groupValues?.getOrNull(1)
            ?: labelledCollector.find(normalized)?.groupValues?.getOrNull(1)
            ?: standaloneCollector.findAll(normalized)
                .map { it.groupValues[1] }
                .firstOrNull { !looksLikeYear(it) }

        val tokens = tokenPattern.findAll(normalized).map { it.value }.toList()
        val languageToken = tokens.firstOrNull { it in languageTokens }
        val language = languageToken?.let(languageTokens::get)
        val known = knownSetCodes.mapTo(LinkedHashSet()) { normalizeToken(it) }
        val candidates = tokens.asSequence()
            .map(::normalizeToken)
            .filter { it.length in 2..6 }
            .filter { it !in languageTokens && it !in ignoredSetTokens }
            .filterNot { it.all(Char::isDigit) }
            .distinct()
            .toList()
        val knownCandidates = candidates.filter { it in known }
        val orderedCandidates = if (knownCandidates.isNotEmpty()) {
            knownCandidates + candidates.filterNot { it in knownCandidates }
        } else {
            prioritizeNearLanguage(lines, languageToken, candidates)
        }
        return PrintingMetadataGuess(
            rawText = rawText.trim(),
            collectorNumber = collector?.uppercase(Locale.US),
            setCode = orderedCandidates.firstOrNull(),
            languageCode = language,
            setCodeCandidates = orderedCandidates
        )
    }

    fun collectorKeysMatch(first: String, second: String): Boolean =
        normalizeCollector(first) == normalizeCollector(second)

    internal fun normalizeCollector(value: String): String {
        val token = value.trim().substringBefore('/').trim().lowercase(Locale.US)
        val match = Regex("^0*([0-9]+)([a-z]*)$").matchEntire(token) ?: return token
        return (match.groupValues[1].trimStart('0').ifEmpty { "0" } + match.groupValues[2])
    }

    private fun prioritizeNearLanguage(
        lines: List<String>,
        languageToken: String?,
        candidates: List<String>
    ): List<String> {
        if (languageToken == null) return candidates
        val languageLine = lines.firstOrNull { line ->
            tokenPattern.findAll(line).any { it.value == languageToken }
        }.orEmpty()
        val nearby = tokenPattern.findAll(languageLine)
            .map { normalizeToken(it.value) }
            .filter { it in candidates }
            .toList()
        return nearby + candidates.filterNot { it in nearby }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase(Locale.US)
        .replace('•', ' ')
        .replace('·', ' ')

    private fun normalizeToken(value: String): String = value.uppercase(Locale.US)
        .filter(Char::isLetterOrDigit)

    private fun looksLikeYear(value: String): Boolean =
        value.takeWhile(Char::isDigit).toIntOrNull() in 1993..2100
}
