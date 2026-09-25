package io.asv.mtgocr.ocrreader

import java.util.Locale

/** Coordinates are normalized to the rectified card, not the enlarged OCR bitmap. */
data class PrintingOcrLine(
    val pass: Int, val text: String,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val elements: List<PrintingOcrElement> = emptyList()
)

data class PrintingOcrElement(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

internal enum class ScanReadState { READ, PARTIAL, OBSERVED_ABSENT, UNREADABLE, NOT_APPLICABLE, CONFLICT }

internal data class LegacyFooter(val number: String, val total: String, val year: String)

internal data class PrintedFooter(val setCode: String, val number: String?, val language: String)

internal data class StructuredPrintingRead(
    val state: ScanReadState,
    val candidates: List<PrintedFooter> = emptyList(),
    val lines: List<PrintingOcrLine> = emptyList(),
    val supportingPasses: Set<Int> = emptySet(),
    val completeTuplePasses: Set<Int> = emptySet(),
    val legacyCandidates: List<LegacyFooter> = emptyList(),
    val historical: HistoricalFooterRead = HistoricalFooterRead()
) {
    val footer: PrintedFooter? get() = candidates.singleOrNull().takeIf { state == ScanReadState.READ }
    // Crops of the same photograph corroborate parsing, not independent physical observations.
    val corroboratedTuple: Boolean get() = footer?.number != null &&
        completeTuplePasses.mapNotNull { when (it) { in 0..2, 7 -> 0; in 3..5, 8 -> 1; 6 -> 2; 9, 10 -> 3; else -> null } }.distinct().size >= 2
}

/** Conservative M15/recent profiles only. No fuzzy codes, invented lines or absent-symbol claims. */
internal object StructuredPrintingEvidence {
    private val footer = Regex("^([A-Z0-9]{2,8})(?:\\s*[•·★☆*.:-]\\s*|\\s+)([A-Z]{2,3})$")
    private val number = Regex("^(?:[CURMSLP]\\s*)?([0-9]{1,5}[A-Z★☆*]?)(?:\\s*/\\s*[0-9]{2,5})?(?:\\s+[CURMSLP])?$")
    private val languages = mapOf("EN" to "en", "ES" to "es", "SP" to "es", "PT" to "pt",
        "FR" to "fr", "DE" to "de", "IT" to "it", "JA" to "ja", "JP" to "ja",
        "KO" to "ko", "KR" to "ko", "RU" to "ru", "CS" to "zhs", "CT" to "zht",
        "ZHS" to "zhs", "ZHT" to "zht")

    fun read(lines: List<PrintingOcrLine>, knownSets: Set<String>): StructuredPrintingRead {
        val known = knownSets.mapTo(HashSet()) { it.uppercase(Locale.ROOT) }
        val observations = mutableListOf<Pair<Int, PrintedFooter>>()
        val valid = lines.filter { validBox(it.left, it.top, it.right, it.bottom) }.flatMap(::prefixes)
        valid.groupBy { it.pass }.forEach { (pass, passLines) ->
            passLines.filter { it.top >= .80f && it.left < .5f }.forEach footerLine@ { line ->
                val raw = line.text.trim().uppercase(Locale.ROOT)
                val match = footer.matchEntire(raw)
                val exact = match?.let { m -> languages[m.groupValues[2]]?.let { lang ->
                    m.groupValues[1].takeIf { it in known }?.let { it to lang }
                } }
                // Dictionary segmentation only: exact concatenation, unique decomposition, no fuzzy edits.
                val joined = if (match == null && raw.length in 4..11 && raw.none(Char::isWhitespace))
                    known.mapNotNull { code -> if (raw.startsWith(code))
                        languages[raw.substring(code.length)]?.let { code to it } else null
                    }.distinct().singleOrNull() else null
                val (code, language) = exact ?: joined ?: return@footerLine
                // Only a preceding, aligned line in THIS processing pass may supply the number.
                val numbers = passLines.filter {
                    it.top >= .78f && it.bottom <= line.top + .008f && line.top - it.bottom <= .065f &&
                        kotlin.math.abs(it.left - line.left) <= .08f
                }.mapNotNull { collectorNumber(it.text) }
                    .map { RulesScanPolicy.collectorKey(it).uppercase(Locale.ROOT) }
                    .distinct()
                if (numbers.isEmpty()) observations += pass to PrintedFooter(code, null, language)
                else numbers.forEach { observations += pass to PrintedFooter(code, it, language) }
            }
        }
        // Same-capture contrast passes support a reading, but are NOT independent confidence votes.
        val candidates = observations.map { it.second }.distinct().filterNot { candidate ->
            candidate.number == null && observations.any {
                it.second.setCode == candidate.setCode && it.second.language == candidate.language && it.second.number != null
            }
        }
        // Older layouts have copyright + collector fraction, not a printed SET/LANGUAGE tuple.
        // Retain partial evidence without manufacturing a set or selecting a printing.
        val legacy = lines.filter { validBox(it.left, it.top, it.right, it.bottom) && it.top >= .90f }
            .mapNotNull { line ->
                val fraction = Regex("(?<![0-9])([0-9]{1,5})\\s*/\\s*([0-9]{2,5})(?![0-9])").find(line.text) ?: return@mapNotNull null
                val year = Regex("(?<![0-9])(?:19|20)[0-9]{2}(?![0-9])").findAll(line.text.take(fraction.range.first)).lastOrNull()?.value
                    ?: return@mapNotNull null
                LegacyFooter(RulesScanPolicy.collectorKey(fraction.groupValues[1]), fraction.groupValues[2], year)
            }.distinct()
        val historical = HistoricalFooterEvidence.read(lines)
        return StructuredPrintingRead(
            when (candidates.size) { 0 -> if (legacy.isNotEmpty() || historical.hasEvidence) ScanReadState.PARTIAL else ScanReadState.UNREADABLE; 1 -> ScanReadState.READ; else -> ScanReadState.CONFLICT },
            candidates, lines, observations.mapTo(linkedSetOf()) { it.first },
            observations.filter { it.second.number != null && it.second == candidates.singleOrNull() }
                .mapTo(linkedSetOf()) { it.first }, legacy, historical
        )
    }

    // Repair only a geometrically aligned numeric line, never titles or set codes.
    // Keep valid suffixes unchanged and require at least one genuine digit.
    internal fun collectorNumber(text: String): String? {
        val raw = text.trim().uppercase(Locale.ROOT)
        number.matchEntire(raw)?.let { return it.groupValues[1] }
        val numeric = Regex("^(?:[CURMSLP]\\s*)?([0-9OIL|]{1,5})([A-Z★☆*]?)(?:\\s*/\\s*([0-9OIL|]{2,5}))?(?:\\s*[CURMSLP])?$")
            .matchEntire(raw) ?: return null
        val digits = numeric.groupValues[1]
        if (digits.none { it in '0'..'9' }) return null
        val repaired = digits.map { when (it) { 'O' -> '0'; 'I', 'L', '|' -> '1'; else -> it } }.joinToString("")
        return repaired + numeric.groupValues[2]
    }

    private fun validBox(left: Float, top: Float, right: Float, bottom: Float) =
        listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f } && left < right && top < bottom

    /** Extract a prefix only at REAL OCR word boundaries. Never truncate a numeric token or invent characters. */
    private fun prefixes(line: PrintingOcrLine): List<PrintingOcrLine> {
        if (line.top < .86f || line.left >= .35f || line.elements.isEmpty()) return listOf(line)
        val elements = line.elements.sortedBy { it.left }
        if (elements.any { !validBox(it.left, it.top, it.right, it.bottom) ||
                it.left < line.left - .005f || it.right > line.right + .005f ||
                it.top < line.top - .005f || it.bottom > line.bottom + .005f }) return listOf(line)
        val prefixes = (1..minOf(4, elements.size)).mapNotNull { size ->
            val parts = elements.take(size)
            if (parts.zipWithNext().any { (a, b) -> b.left - a.right > .045f }) return@mapNotNull null
            // Do not truncate a split number, fraction, rarity or collector suffix.
            if (elements.getOrNull(size)?.text?.matches(Regex("^[0-9/].*|^[A-Za-z★☆*]$")) == true) return@mapNotNull null
            line.copy(text = parts.joinToString(" ") { it.text }, left = parts.first().left,
                top = parts.minOf { it.top }, right = parts.last().right, bottom = parts.maxOf { it.bottom }, elements = emptyList())
        }
        return listOf(line) + prefixes
    }
}
