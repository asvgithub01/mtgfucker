package io.asv.mtgocr.ocrreader

/** Diagnostic comparison only. Never removes candidates or supplies an automatic selection. */
internal object HistoricalPrintingComparison {
    enum class Match { MATCH, DIFFERENT, UNKNOWN, OCR_CONFLICT }
    data class Candidate(
        val uuid: String, val set: String, val setName: String, val number: String,
        val releaseYear: String, val numberMatch: Match, val releaseYearMatch: Match,
        val retainedFooterPossible: Boolean,
        val artist: String = "", val artistMatch: Match = Match.UNKNOWN,
        val languages: List<String> = emptyList(), val languageMatch: Match = Match.UNKNOWN
    )
    data class Result(val state: String, val identity: String? = null,
        val identitySource: String = "NONE", val candidates: List<Candidate> = emptyList())

    fun evaluate(art: List<RulesAutoAddPolicy.Artwork>, names: List<String>,
        footer: StructuredPrintingRead, setTypes: Map<String, String>,
        artistReferences: Map<String, String> = emptyMap(),
        observedLanguage: String = "", languageState: ScanReadState = ScanReadState.UNREADABLE): Result {
        val historical = footer.historical
        if (!historical.hasEvidence || footer.candidates.isNotEmpty()) return Result("NOT_APPLICABLE")
        val identities = names.filter { it.isNotBlank() }.distinctBy { it.lowercase(java.util.Locale.ROOT) }
        if (identities.size > 1) return Result("IDENTITY_CONFLICT")
        val visual = RulesArtworkEvidence.reason(art, names)
        if (visual == "OCR_CONFLICT") return Result("IDENTITY_CONFLICT")
        val name = identities.singleOrNull() ?: art.firstOrNull()?.name?.takeIf { visual == "STRONG_ART" }
            ?: return Result("IDENTITY_UNKNOWN")
        val source = if (identities.isEmpty()) "STRONG_ART" else "OCR"
        // An OCR title does not prove which illustration it is. Retain every matching visual group.
        val variants = art.filter { HashScanEvidence.namesEquivalent(it.name, name) }
            .flatMap { it.variants }.filter { HashScanEvidence.namesEquivalent(it.cardName, name) }
        val year = historical.years.values.map { it.substringAfterLast('–') }.distinct().singleOrNull()
        val number = historical.collectorNumbers.values.singleOrNull()
        fun compare(field: FooterField, observed: String?, catalog: String): Match = when {
            field.state == ScanReadState.CONFLICT -> Match.OCR_CONFLICT
            field.state != ScanReadState.READ || observed.isNullOrBlank() || catalog.isBlank() -> Match.UNKNOWN
            observed == catalog -> Match.MATCH
            else -> Match.DIFFERENT
        }
        val types = setTypes.mapKeys { it.key.uppercase(java.util.Locale.ROOT) }
        val candidates = variants.groupBy { it.printingUuid }.map { (uuid, group) ->
            // Conflicting catalogue metadata must remain unknown, not arbitrarily take the first row.
            val first = group.first()
            val code = first.set.code.uppercase(java.util.Locale.ROOT)
            val numbers = group.map { RulesScanPolicy.collectorKey(it.collectorNumber) }.distinct()
            val years = group.map { it.set.releaseDate.take(4).takeIf { s -> s.matches(Regex("[0-9]{4}")) }.orEmpty() }.distinct()
            val catalogNumber = numbers.singleOrNull().orEmpty()
            val releaseYear = years.singleOrNull().orEmpty()
            val referenceArtists = art.filter { artwork -> artwork.variants.any { it.printingUuid == uuid } }
                .map { artwork -> artwork.id?.let { artistReferences[PrintingArtistIndex.key(uuid, it)] }.orEmpty() }
                .distinct()
            val artist = referenceArtists.singleOrNull().orEmpty()
            val artistRead = historical.artists
            val artistMatch = when {
                artist.isBlank() || artistRead.values.isEmpty() -> Match.UNKNOWN
                artistRead.state == ScanReadState.CONFLICT -> Match.OCR_CONFLICT
                normalizeArtist(artistRead.values.single()) == normalizeArtist(artist) -> Match.MATCH
                else -> Match.DIFFERENT
            }
            val languages = group.map { it.languageCode }.filter { it.isNotBlank() }.distinct().sorted()
            // Missing foreign rows in APV1 are not evidence that a language never existed.
            val languageMatch = when {
                languageState == ScanReadState.CONFLICT -> Match.OCR_CONFLICT
                languageState == ScanReadState.READ && observedLanguage in languages -> Match.MATCH
                else -> Match.UNKNOWN
            }
            Candidate(uuid, code, first.set.name, catalogNumber, releaseYear,
                compare(historical.collectorNumbers, number?.let(RulesScanPolicy::collectorKey), catalogNumber),
                compare(historical.years, year, releaseYear),
                code in setOf("PLST", "MB1", "FMB1", "MB2") || types[code] == "promo" ||
                    types[code] !in setOf("core", "expansion", "masters", "draft_innovation"), artist, artistMatch, languages, languageMatch)
        }.sortedWith(compareByDescending<Candidate> { it.numberMatch == Match.MATCH }
            .thenByDescending { it.artistMatch == Match.MATCH }
            .thenByDescending { it.releaseYearMatch == Match.MATCH }.thenBy { it.set }.thenBy { it.number }.thenBy { it.uuid })
        return Result(if (candidates.isEmpty()) "NO_INDEX_CANDIDATES" else "REVIEW_ONLY", name, source, candidates)
    }
    // Accent/case/spacing normalization only. No fuzzy repair, missing-word acceptance or voting.
    private fun normalizeArtist(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT).trim().replace(Regex("\\s+"), " ")
}
