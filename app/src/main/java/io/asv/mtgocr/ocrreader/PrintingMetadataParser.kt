package io.asv.mtgocr.ocrreader

import java.text.Normalizer
import java.util.Locale

data class PrintingMetadataGuess(
    val rawText: String,
    val collectorNumber: String?,
    val setCode: String?,
    val languageCode: String?,
    val printingYear: Int?,
    val setCodeCandidates: List<String>
)

/** Extracts the stable printing tokens from OCR of a rectified card's lower band. */
object PrintingMetadataParser {
    private val collectorWithTotal = Regex(
        "(?i)(?<![0-9])([0-9oil]{1,4}[a-z]?)\\s*[/|]\\s*[0-9oil]{1,4}[a-z]?(?![0-9])"
    )
    private val labelledCollector = Regex(
        "(?i)(?:#|N[O0]\\.?\\s*)([0-9oil]{1,4}[a-z]?)(?![0-9])"
    )
    private val standaloneCollector = Regex("(?i)(?<![a-z0-9])([0-9]{1,4}[a-z]?)(?![a-z0-9])")
    private val tokenPattern = Regex("[A-Z0-9]{2,6}")
    private val yearPattern = Regex("(?i)(?<![a-z0-9])([12oil][0-9oil]{3})(?![a-z0-9])")
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
        val printingYear = yearPattern.findAll(normalized)
            .mapNotNull { repairCollectorOcr(it.groupValues[1]).toIntOrNull() }
            .firstOrNull { it in 1993..2100 }
        val known = knownSetCodes.mapTo(LinkedHashSet()) { normalizeToken(it) }
        val candidates = tokens.asSequence()
            .map(::normalizeToken)
            .filter { it.length in 2..6 }
            .filter { it !in languageTokens && it !in ignoredSetTokens }
            .filterNot { it.all(Char::isDigit) }
            .distinct()
            .toList()
        val knownCandidates = candidates.filter { it in known }
        val fuzzyKnownCandidates = candidates.asSequence()
            .filter { it.length >= 3 }
            .flatMap { candidate ->
                known.asSequence().filter { knownCode ->
                    knownCode.length == candidate.length && editDistance(candidate, knownCode) <= 1
                }
            }
            .distinct()
            .toList()
        val orderedCandidates = if (knownCandidates.isNotEmpty()) {
            knownCandidates + fuzzyKnownCandidates.filterNot { it in knownCandidates } +
                candidates.filterNot { it in knownCandidates || it in fuzzyKnownCandidates }
        } else if (fuzzyKnownCandidates.isNotEmpty()) {
            fuzzyKnownCandidates + candidates.filterNot { it in fuzzyKnownCandidates }
        } else {
            prioritizeNearLanguage(lines, languageToken, candidates)
        }
        return PrintingMetadataGuess(
            rawText = rawText.trim(),
            collectorNumber = collector?.let(::repairCollectorOcr)?.uppercase(Locale.US),
            setCode = orderedCandidates.firstOrNull(),
            languageCode = language,
            printingYear = printingYear,
            setCodeCandidates = orderedCandidates
        )
    }

    fun collectorKeysMatch(first: String, second: String): Boolean =
        normalizeCollector(first) == normalizeCollector(second)

    internal fun normalizeCollector(value: String): String {
        val token = repairCollectorOcr(value.trim().substringBefore('/').trim())
            .lowercase(Locale.US)
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
        repairCollectorOcr(value).takeWhile(Char::isDigit).toIntOrNull() in 1993..2100

    private fun repairCollectorOcr(value: String): String = value.map { character ->
        when (character.uppercaseChar()) {
            'O' -> '0'
            'I', 'L' -> '1'
            else -> character
        }
    }.joinToString("")

    private fun editDistance(first: String, second: String): Int {
        if (first == second) return 0
        var previous = IntArray(second.length + 1) { it }
        first.forEachIndexed { firstIndex, firstCharacter ->
            val current = IntArray(second.length + 1)
            current[0] = firstIndex + 1
            second.forEachIndexed { secondIndex, secondCharacter ->
                current[secondIndex + 1] = minOf(
                    current[secondIndex] + 1,
                    previous[secondIndex + 1] + 1,
                    previous[secondIndex] + if (firstCharacter == secondCharacter) 0 else 1
                )
            }
            previous = current
        }
        return previous[second.length]
    }
}
