package io.asv.mtgocr.ocrreader

/** Observations, not catalogue facts. Never sufficient to select a printing by themselves. */
internal data class FooterObservation(val value: String, val source: PrintingOcrLine,
    val originalValue: String? = null, val normalization: String? = null)
internal data class FooterField(val observations: List<FooterObservation> = emptyList(),
    val copyrightYears: Boolean = false
) {
    val values get() = observations.map { it.value }.distinct()
    val state get() = when (values.size) {
        0 -> ScanReadState.UNREADABLE
        1 -> ScanReadState.READ
        else -> if (copyrightYears && compatibleYearReadings(values)) ScanReadState.READ else ScanReadState.CONFLICT
    }
    private fun compatibleYearReadings(values: List<String>): Boolean {
        // One explicit range plus its terminal year may be a cropped reading.
        // Different ranges or different standalone years remain contradictory; no majority vote.
        val range = values.filter { it.matches(Regex("[0-9]{4}–[0-9]{4}")) }.singleOrNull() ?: return false
        val (start, end) = range.split('–').map(String::toInt)
        return start <= end && values.all { it == range || it == end.toString() }
    }
}
internal data class HistoricalFooterRead(
    val years: FooterField = FooterField(),
    val artists: FooterField = FooterField(),
    val fractions: FooterField = FooterField()
) {
    // Project accepted tuples independently; never combine observations into a new fraction.
    val collectorNumbers: FooterField get() = fractionPart(0)
    val printedTotals: FooterField get() = fractionPart(1)
    private fun fractionPart(index: Int) = FooterField(fractions.observations.mapNotNull { observation ->
        val parts = observation.value.split('/')
        if (parts.size != 2 || parts.any { !it.matches(Regex("[0-9]+")) }) null
        else observation.copy(value = parts[index])
    })
    val hasEvidence get() = listOf(years, artists, fractions).any { it.observations.isNotEmpty() }
}

internal object HistoricalFooterEvidence {
    private val year = Regex("(?<![0-9A-Za-z])([12IiLl|][0-9OoIiLl|]{3})(?![0-9])")
    private val copyright = Regex("©|\\bcopyright\\b|wizards|\\bcost\\b|\\bcoast\\b|^[^\\p{L}0-9]*[Cc]?[12][0-9OoIiLl|]{3}", RegexOption.IGNORE_CASE)
    private val fraction = Regex("(?<![0-9A-Za-z])([0-9OoIiLl|]{1,5})\\s*/\\s*([0-9OoIiLl|]{2,5})(?![0-9A-Za-z])")
    private val trademarkCopyright = Regex("^(?:TM|M|™)\\s*&\\s*C\\s*(?=[12IiLl|][0-9OoIiLl|]{3}(?![0-9]))", RegexOption.IGNORE_CASE)
    private val artistPrefix = Regex("^(?:il{1,2}us(?:c|t(?:r)?)?|ilustr(?:ador)?)[.,:]?\\s+", RegexOption.IGNORE_CASE)
    private fun digits(raw: String) = raw.map { when (it) {
        'O', 'o' -> '0'; 'I', 'i', 'L', 'l', '|' -> '1'; else -> it
    } }.joinToString("")

    fun read(lines: List<PrintingOcrLine>): HistoricalFooterRead {
        val valid = lines.filter { line ->
            listOf(line.left, line.top, line.right, line.bottom).all { it.isFinite() && it in 0f..1f } &&
                line.left < line.right && line.top < line.bottom && line.top >= .89f
        }
        val years = mutableListOf<FooterObservation>()
        val fractions = mutableListOf<FooterObservation>()
        val artists = mutableListOf<FooterObservation>()
        fun artistName(text: String): String? {
            val name = artistPrefix.replace(text.trim(), "").trim()
                .replace(Regex("^©\\s*"), "").trim()
            // Only the actual copyright glyph is removed. An OCR O/C may be part of a name.
            return name.takeIf { it.length in 5..60 && it.split(Regex("\\s+")).size in 2..5 &&
                it.all { char -> char.isLetter() || char in " .'-’" } && !copyright.containsMatchIn(it) }
        }
        valid.forEach { line ->
            // An explicit illustrator label is useful even when the copyright is unreadable.
            if (artistPrefix.containsMatchIn(line.text.trim())) artistName(line.text)?.let {
                artists += FooterObservation(it, line)
            }
            fraction.findAll(line.text).forEach { match ->
                val a = match.groupValues[1]; val b = match.groupValues[2]
                val n = digits(a).toIntOrNull(); val total = digits(b).toIntOrNull()
                if (a.any(Char::isDigit) && b.any(Char::isDigit) && n != null && total != null && n in 1..total)
                    fractions += FooterObservation("$n/$total", line)
            }
            // OCR may read the copyright glyph as C after a trademark marker. Normalize only
            // this explicit prefix immediately before a four-character numeric year candidate.
            val copyrightText = trademarkCopyright.replaceFirst(line.text.trim(), "© ")
            if (!copyright.containsMatchIn(copyrightText)) return@forEach
            // Preserve every year in a copyright range; do not invent a release year.
            val yearText = copyrightText.replace(Regex("^[Cc](?=[12])"), "© ")
            val yearMatches = year.findAll(yearText)
                .filter { digits(it.groupValues[1]).toInt() in 1993..2099 }.toList()
            val found = yearMatches.map { digits(it.groupValues[1]) }.distinct()
            if (found.isEmpty()) return@forEach
            // A crop such as 1993-20 is an incomplete range, not a standalone 1993 copyright.
            // Keep its raw OCR, but do not compare the range start with a printing's release year.
            val incompleteRange = yearMatches.size == 1 && yearText.substring(yearMatches.single().range.last + 1)
                .trimStart().firstOrNull() in listOf('-', '–', '—')
            if (!incompleteRange) years += FooterObservation(found.joinToString("–"), line)
            valid.filter { it.pass == line.pass && it !== line && it.bottom <= line.top + .008f &&
                line.top - it.bottom in -.008f.. .035f &&
                    (it.left < .5f || (it.top >= .92f && it.right > .65f &&
                        it.left <= line.right && it.right >= line.left))
            }.forEach { above ->
                // Unlabelled names need an adjacent copyright anchor in the same pass.
                artistName(above.text)?.let { artists += FooterObservation(it, above) }
            }
        }
        return HistoricalFooterRead(FooterField(years.distinct(), copyrightYears = true), FooterField(artists.distinct()), FooterField(fractions.distinct()))
    }
}
