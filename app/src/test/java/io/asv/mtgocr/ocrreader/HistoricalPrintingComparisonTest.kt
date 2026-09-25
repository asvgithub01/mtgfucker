package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class HistoricalPrintingComparisonTest {
    private fun variant(id: String = "one", set: String = "SCG", number: String = "129", year: String = "2003") =
        ArtPrintingIndex.Variant(id, "00000000-0000-0000-0000-000000000001", "Card", "Carta", number,
            ArtPrintingIndex.SetInfo(set, set, year, "", null, null), "es", CardBorderColor.BLACK, 3, "rare", 0, null, null)
    private fun line(text: String, pass: Int = 0) = PrintingOcrLine(pass, text, .1f, .94f, .9f, .96f)
    private fun footer(vararg texts: String) = StructuredPrintingEvidence.read(texts.mapIndexed { i, s -> line(s, i) }, emptySet())
    private fun read(variants: List<ArtPrintingIndex.Variant>, foot: StructuredPrintingRead = footer("©1993-2003 Wizards 129/143"),
        names: List<String> = listOf("Card"), p: Int = 4) = HistoricalPrintingComparison.evaluate(
        listOf(RulesAutoAddPolicy.Artwork("a", "Card", p, 4, variants),
            RulesAutoAddPolicy.Artwork("b", "Other", 18, 22, emptyList())), names, foot,
        mapOf("SCG" to "expansion", "NEW" to "expansion"))
    @Test fun numberAndReleaseYearMatchesRankFirstWithoutRemovingOthers() {
        val result = read(listOf(variant("later", "NEW", "12", "2020"), variant()))
        assertEquals("REVIEW_ONLY", result.state)
        assertEquals(2, result.candidates.size)
        assertEquals("one", result.candidates.first().uuid)
        assertEquals(HistoricalPrintingComparison.Match.MATCH, result.candidates.first().numberMatch)
        assertEquals(HistoricalPrintingComparison.Match.MATCH, result.candidates.first().releaseYearMatch)
        assertEquals(HistoricalPrintingComparison.Match.DIFFERENT, result.candidates.last().releaseYearMatch)
    }
    @Test fun reprintsWithRetainedFooterRemainCandidates() {
        val result = read(listOf(variant(), variant("list", "PLST", "SCG-129", "2022")))
        assertEquals(2, result.candidates.size)
        assertTrue(result.candidates.single { it.set == "PLST" }.retainedFooterPossible)
    }
    @Test fun unknownCatalogYearAndNumberAreNotMismatches() {
        val candidate = read(listOf(variant(number="", year=""))).candidates.single()
        assertEquals(HistoricalPrintingComparison.Match.UNKNOWN, candidate.numberMatch)
        assertEquals(HistoricalPrintingComparison.Match.UNKNOWN, candidate.releaseYearMatch)
    }
    @Test fun ocrConflictsCannotBeResolvedByCatalogOrMajority() {
        val candidate = read(listOf(variant()), footer("©2003 Wizards 129/143", "©2004 Wizards 128/143")).candidates.single()
        assertEquals(HistoricalPrintingComparison.Match.OCR_CONFLICT, candidate.numberMatch)
        assertEquals(HistoricalPrintingComparison.Match.OCR_CONFLICT, candidate.releaseYearMatch)
    }
    @Test fun missingFractionDoesNotEraseYearHint() {
        val candidate = read(listOf(variant()), footer("©2003 Wizards")).candidates.single()
        assertEquals(HistoricalPrintingComparison.Match.UNKNOWN, candidate.numberMatch)
        assertEquals(HistoricalPrintingComparison.Match.MATCH, candidate.releaseYearMatch)
    }
    @Test fun identityConflictBlocksComparisonAndWeakArtCannotInventIdentity() {
        assertEquals("IDENTITY_CONFLICT", read(listOf(variant()), names=listOf("Other")).state)
        assertEquals("IDENTITY_UNKNOWN", read(listOf(variant()), names=emptyList(), p=14).state)
        assertEquals("STRONG_ART", read(listOf(variant()), names=emptyList()).identitySource)
    }
    @Test fun languageRowsDoNotCountAsDifferentPrintings() {
        val v = variant()
        assertEquals(1, read(listOf(v, v.copy(languageCode="en"))).candidates.size)
    }
    @Test fun modernFooterRemainsOnExistingRoute() {
        val modern = StructuredPrintingRead(ScanReadState.READ, listOf(PrintedFooter("ORI", "57", "en")))
        assertEquals("NOT_APPLICABLE", read(listOf(variant()), modern).state)
    }
    @Test fun missingVisualCoverageDoesNotClaimNoPrintingExists() {
        assertEquals("NO_INDEX_CANDIDATES", read(emptyList()).state)
    }
    private fun artistComparison(texts: List<String>, reference: String? = "Wayne England"):
        HistoricalPrintingComparison.Candidate {
        val historical = footer("©2003 Wizards").historical.copy(artists = FooterField(texts.map { FooterObservation(it, line("Illus. $it")) }))
        return HistoricalPrintingComparison.evaluate(listOf(RulesAutoAddPolicy.Artwork("a", "Card", 4, 4, listOf(variant()))),
            listOf("Card"), StructuredPrintingRead(ScanReadState.PARTIAL, historical = historical), emptyMap(),
            reference?.let { mapOf(PrintingArtistIndex.key("one", "a") to it) }.orEmpty()).candidates.single()
    }
    @Test fun exactArtistReferenceNormalizesOnlyCaseAccentAndSpacing() {
        assertEquals(HistoricalPrintingComparison.Match.MATCH, artistComparison(listOf("  Wáyne   England  ")).artistMatch)
    }
    @Test fun artistConflictRemainsEvenWhenOneAlternativeMatchesCatalog() {
        assertEquals(HistoricalPrintingComparison.Match.OCR_CONFLICT,
            artistComparison(listOf("Wayne England", "Wayne Englarnd")).artistMatch)
    }
    @Test fun absentReferenceNeverManufacturesAMatch() {
        assertEquals(HistoricalPrintingComparison.Match.UNKNOWN, artistComparison(listOf("Wayne England"), null).artistMatch)
        assertEquals(HistoricalPrintingComparison.Match.UNKNOWN, artistComparison(emptyList()).artistMatch)
    }
    @Test fun differentArtistRemainsDifferentWithoutFuzzyRepair() {
        assertEquals(HistoricalPrintingComparison.Match.DIFFERENT, artistComparison(listOf("Wayne Englarnd")).artistMatch)
    }
    @Test fun languageCompatibilityIsPositiveOnlyNeverExcludesMissingForeignRows() {
        val variants = listOf(variant(), variant("two", "NEW").copy(languageCode = "en"))
        val arts = listOf(RulesAutoAddPolicy.Artwork("a", "Card", 4, 4, variants))
        val result = HistoricalPrintingComparison.evaluate(arts, listOf("Card"), footer("©2003 Wizards"),
            emptyMap(), observedLanguage = "es", languageState = ScanReadState.READ)
        assertEquals(2, result.candidates.size)
        assertEquals(HistoricalPrintingComparison.Match.MATCH, result.candidates.single { it.uuid == "one" }.languageMatch)
        assertEquals(HistoricalPrintingComparison.Match.UNKNOWN, result.candidates.single { it.uuid == "two" }.languageMatch)
    }
    @Test fun matchingNumeratorSurvivesTotalConflictWithoutRemovingOtherPrintings() {
        val result = read(listOf(variant(), variant("other", number = "128")), footer("©2003 Wizards 129/143", "©2003 Wizards 129/149"))
        assertEquals(2, result.candidates.size)
        assertEquals(HistoricalPrintingComparison.Match.MATCH, result.candidates.single { it.uuid == "one" }.numberMatch)
        assertEquals(HistoricalPrintingComparison.Match.DIFFERENT, result.candidates.single { it.uuid == "other" }.numberMatch)
        assertEquals("REVIEW_ONLY", result.state)
    }
    @Test fun conflictingNumeratorsCannotBeRepairedByCatalogMatch() {
        val result = read(listOf(variant()), footer("©2003 Wizards 129/143", "©2003 Wizards 128/143"))
        assertEquals(HistoricalPrintingComparison.Match.OCR_CONFLICT, result.candidates.single().numberMatch)
    }
}
