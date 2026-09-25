package io.asv.mtgocr.ocrreader

import java.util.Locale

/** Evidence alone never authorizes a save. RulesAutoAddPolicy owns the operational gate. No Android/IO. */
internal object RulesScanPolicy {
    enum class Status { NO_MATCH, IDENTITY_ONLY, PRINTING_CANDIDATE, CONFLICT }
    data class Art(val name: String, val phash: Int, val dhash: Int)
    data class Printing(val uuid: String, val name: String, val set: String, val number: String)
    data class Input(
        val names: List<String>, val art: List<Art>, val printings: List<Printing>,
        val footer: StructuredPrintingRead,
        val rulesLanguage: String = "", val rulesConfidence: Float = 0f, val rulesText: String = "",
        val titleLanguage: TitleLanguageIndex.Evidence = TitleLanguageIndex.Evidence()
    )
    data class Decision(
        val status: Status, val identities: List<String>, val printingUuids: List<String>,
        val language: String, val languageState: ScanReadState, val ruleIds: List<String>
    ) {
        val coverage: String get() = "ART_INDEX_ONLY"
    }

    fun evaluate(input: Input): Decision {
        val ids = linkedSetOf("D02", "F01")
        val names = input.names.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }
        val strongArt = input.art.filter { it.phash in 0..10 && it.dhash in 0..16 }
        val identities = names.ifEmpty { strongArt.map { it.name }.distinct() }
        ids += if (names.isNotEmpty()) "I01" else "I04"
        var conflict = input.footer.state == ScanReadState.CONFLICT
        if (conflict) ids += "P12"
        // Missing OCR identity from a truncated visual shortlist is not an exclusion.
        if (names.isNotEmpty() && strongArt.isNotEmpty() && strongArt.none { art ->
                names.any { HashScanEvidence.namesEquivalent(it, art.name) }
            }) { conflict = true; ids += "I06" }
        val reliableRulesLanguage = input.rulesLanguage.takeIf {
            it.isNotBlank() && input.rulesConfidence.isFinite() && input.rulesConfidence in .80f..1f &&
                input.rulesText.count(Char::isLetter) >= 20
        }.orEmpty()
        val printed = input.footer.footer
        val title = input.titleLanguage
        val constraints = listOfNotNull(printed?.language?.let { setOf(it) },
            reliableRulesLanguage.takeIf { it.isNotBlank() }?.let { setOf(it) },
            title.candidates.takeIf { title.matches.isNotEmpty() })
        val compatibleLanguages = constraints.reduceOrNull { a, b -> a intersect b }.orEmpty()
        val languageConflict = title.conflict || (constraints.isNotEmpty() && compatibleLanguages.isEmpty())
        if (languageConflict) { conflict = true; ids += "L09" }
        val language = if (languageConflict || input.footer.state == ScanReadState.CONFLICT) ""
            else compatibleLanguages.singleOrNull().orEmpty()
        if (title.matches.isNotEmpty()) ids += if (title.candidates.size == 1) "L02" else "L03"
        if (printed != null) ids += "L01"
        if (reliableRulesLanguage.isNotBlank()) ids += "L04"
        if (language.isBlank()) ids += "L05"
        // No border/language/default-finish filtering can manufacture uniqueness here.
        var candidates = input.printings.filter { p -> identities.any { HashScanEvidence.namesEquivalent(it, p.name) } }
        if (printed != null) {
            ids += if (printed.number == null) "P02" else "P01"
            candidates = candidates.filter { p -> p.set.equals(printed.setCode, true) &&
                (printed.number == null || collectorKey(p.number) == collectorKey(printed.number)) }
            ids += "P10" // Retained footers/marks are not yet observable: remain a candidate, never exact.
        }
        if (candidates.isEmpty() && identities.isNotEmpty()) ids += "I08"
        val uuids = candidates.map { it.uuid }.distinct()
        val status = when {
            conflict -> Status.CONFLICT
            identities.isEmpty() -> Status.NO_MATCH
            printed?.number != null && uuids.size == 1 -> Status.PRINTING_CANDIDATE
            else -> Status.IDENTITY_ONLY
        }
        return Decision(status, identities, uuids, language,
            if (languageConflict || input.footer.state == ScanReadState.CONFLICT) ScanReadState.CONFLICT
            else if (language.isBlank()) ScanReadState.UNREADABLE else ScanReadState.READ, ids.toList())
    }

    /** Exact suffix preserved: do not turn O/I inside a collector suffix into a digit. */
    internal fun collectorKey(raw: String): String {
        val token = raw.trim().lowercase(Locale.ROOT)
        val match = Regex("^0*([0-9]+)([a-z★☆*]?)$").matchEntire(token) ?: return token
        return match.groupValues[1].trimStart('0').ifEmpty { "0" } + match.groupValues[2]
    }
}
