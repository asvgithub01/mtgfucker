package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RulesAutoAddPolicyTest {
    private fun variant(id: String = "one", set: String = "SPG", lang: String = "es", finishes: Int = 3) =
        ArtPrintingIndex.Variant(id, "00000000-0000-0000-0000-000000000001", "Card", "Carta", "150",
            ArtPrintingIndex.SetInfo(set, set, "2026", "", null, null), lang, CardBorderColor.BLACK,
            finishes, "rare", 0, null, null)
    private val unreadable = StructuredPrintingRead(ScanReadState.UNREADABLE)
    private val evidence = RulesScanPolicy.Decision(RulesScanPolicy.Status.IDENTITY_ONLY,
        listOf("Card"), listOf("one"), "es", ScanReadState.READ, emptyList())
    private fun top(variants: List<ArtPrintingIndex.Variant> = listOf(variant())) =
        RulesAutoAddPolicy.Artwork("art", "Card", 4, 10, variants)
    private val other = RulesAutoAddPolicy.Artwork("other", "Other", 12, 20, emptyList())
    private fun evaluate(top: RulesAutoAddPolicy.Artwork = top(), rest: List<RulesAutoAddPolicy.Artwork> = listOf(other),
        names: List<String> = listOf("Card"), ev: RulesScanPolicy.Decision = evidence,
        footer: StructuredPrintingRead = unreadable, enabled: Boolean = true, finish: String = "nonfoil",
        errors: Boolean = false, photo: Boolean = true, types: Map<String, String> = emptyMap()) =
        RulesAutoAddPolicy.evaluate(listOf(top) + rest, names, ev, footer, enabled, finish, errors, photo, types)

    @Test fun uniquePrintingAcrossLanguagesUsesObservedLanguage() {
        val decision = evaluate(top(listOf(variant(lang = "en"), variant(lang = "es"))))
        assertTrue(decision.canAutoAdd)
        assertEquals("es", decision.variant?.languageCode)
        assertEquals("CATALOG_UNIQUE_ARTWORK", decision.reason)
    }
    @Test fun strongArtDoesNotRequireSuccessfulTitleOcr() {
        assertTrue(evaluate(names = emptyList()).canAutoAdd)
    }
    @Test fun ocrAloneNeverRescuesWeakHash() {
        assertEquals("WEAK_ART", evaluate(top().copy(phash = 12, dhash = 30)).reason)
        assertFalse(evaluate(top().copy(phash = -1)).canAutoAdd)
    }
    @Test fun closeOrMissingRunnerUpBlocksAutomation() {
        assertEquals("ART_MARGIN", evaluate(rest = listOf(other.copy(phash = 7))).reason)
        assertFalse(evaluate(rest = emptyList()).canAutoAdd)
    }
    @Test fun strongAlternateArtworkOfSameNameStillBlocks() {
        assertFalse(evaluate(rest = listOf(other.copy(name = "Card", phash = 5))).canAutoAdd)
    }
    @Test fun allOcrIdentitiesMustAgree() {
        assertEquals("OCR_CONFLICT", evaluate(names = listOf("Other")).reason)
        assertFalse(evaluate(names = listOf("Card", "Other")).canAutoAdd)
    }
    @Test fun languageCannotEraseOtherSetToCreateUniqueness() {
        assertEquals("RETAINED_FOOTER_POSSIBLE", evaluate(top(listOf(variant(), variant(set = "PLST", lang = "en")))).reason)
    }
    @Test fun finishCannotEraseOtherPrintingToCreateUniqueness() {
        assertEquals("MULTIPLE_PRINTINGS", evaluate(top(listOf(variant(), variant(id = "foil", finishes = 2)))).reason)
    }
    @Test fun sameSetDifferentCollectorNumbersRemainAmbiguous() {
        assertFalse(evaluate(top(listOf(variant(), variant(id = "two").copy(collectorNumber = "151")))).canAutoAdd)
    }
    @Test fun knownFooterContradictionsAlwaysBlock() {
        fun footer(set: String, number: String?) = StructuredPrintingRead(ScanReadState.READ, listOf(PrintedFooter(set, number, "es")))
        assertEquals("FOOTER_CONFLICT", evaluate(footer = footer("SOA", null)).reason)
        assertEquals("FOOTER_CONFLICT", evaluate(footer = footer("SPG", "151")).reason)
        assertTrue(evaluate(footer = footer("SPG", "0150")).canAutoAdd)
    }
    @Test fun unknownLanguageNeverFallsBackToEnglish() {
        assertEquals("LANGUAGE_UNKNOWN", evaluate(ev = evidence.copy(language = "", languageState = ScanReadState.UNREADABLE)).reason)
        assertEquals("LANGUAGE_NOT_IN_CATALOGUE", evaluate(top(listOf(variant(lang = "en")))).reason)
    }
    @Test fun foilPreferenceIsHonoredButNeverInventedWhenUnavailable() {
        assertEquals("foil", evaluate(finish = "foil").finish)
        assertEquals("FINISH_UNAVAILABLE", evaluate(top(listOf(variant(finishes = 1))), finish = "foil").reason)
        assertFalse(evaluate(finish = "unknown").canAutoAdd)
    }
    @Test fun excludedHistoricalSetAndBackFaceStayManual() {
        assertEquals("HISTORICAL_SET", evaluate(top(listOf(variant(set = "LEA")))).reason)
        assertEquals("UNSUPPORTED_FACE", evaluate(top(listOf(variant().copy(face = 1)))).reason)
    }
    @Test fun disabledErrorMissingPhotoAndConflictStayManual() {
        assertEquals("DISABLED", evaluate(enabled = false).reason)
        assertFalse(evaluate(errors = true).canAutoAdd)
        assertFalse(evaluate(photo = false).canAutoAdd)
        assertFalse(evaluate(ev = evidence.copy(status = RulesScanPolicy.Status.CONFLICT)).canAutoAdd)
        assertFalse(evaluate(footer = StructuredPrintingRead(ScanReadState.CONFLICT)).canAutoAdd)
    }
    @Test fun catalogueIsRequiredAndCannotHaveInconsistentNumbers() {
        assertEquals("NO_ART_CATALOGUE", evaluate(top(emptyList())).reason)
        assertEquals("INCONSISTENT_CATALOGUE", evaluate(top(listOf(variant(), variant().copy(collectorNumber = "151")))).reason)
    }
    @Test fun realSoa52AndSpg150AssetsResolveWithMeasuredCaptureDistances() {
        val index = ArtPrintingIndex.read(File("src/main/assets/art_printing_index.bin").inputStream())
        val cases = listOf(
            Triple("f9d20dbf-0d7b-4b41-9b9d-279db5e373eb", "SOA", 6 to 6),
            Triple("d275436b-3cfb-45a2-b835-526f5307347b", "SPG", 4 to 10))
        cases.forEach { (art, set, distance) ->
            val variants = index.variants(art)
            val name = variants.first().cardName
            val decision = evaluate(RulesAutoAddPolicy.Artwork(art, name, distance.first, distance.second, variants),
                names = listOf(name))
            assertTrue("$set: ${decision.reason}", decision.canAutoAdd)
            assertEquals(set, decision.variant?.set?.code)
            assertEquals("es", decision.variant?.languageCode)
            assertEquals(if (set == "SOA") "52" else "150", decision.variant?.collectorNumber)
        }
    }

    private val baseTypes = mapOf("M19" to "core", "M20" to "core")
    private fun fullFooter(set: String = "M20", number: String = "150", passes: Set<Int> = setOf(0, 3)) =
        StructuredPrintingRead(ScanReadState.READ, listOf(PrintedFooter(set, number, "es")), completeTuplePasses = passes)
    private fun sharedArt() = top(listOf(variant(set = "M19"), variant(id = "two", set = "M20")))

    @Test fun sameArtworkAcrossModernSetsResolvesOnlyWithFullCorroboratedFooter() {
        val result = evaluate(sharedArt(), footer = fullFooter(), types = baseTypes)
        assertEquals("STRUCTURED_FOOTER", result.reason)
        assertEquals("two", result.variant?.printingUuid)
        assertEquals("M20", result.variant?.set?.code)
    }
    @Test fun footerDoesNotAuthorizeAWeakOrAmbiguousArtwork() {
        assertFalse(evaluate(sharedArt().copy(phash = 14), footer = fullFooter(), types = baseTypes).canAutoAdd)
        assertFalse(evaluate(sharedArt(), rest = listOf(other.copy(phash = 5)), footer = fullFooter(), types = baseTypes).canAutoAdd)
    }
    @Test fun footerMustBeCorroboratedAcrossCropFamilies() {
        assertEquals("FOOTER_NEEDS_CORROBORATION", evaluate(sharedArt(), footer = fullFooter(passes = setOf(0, 1, 2)), types = baseTypes).reason)
    }
    @Test fun knownRetainedFooterAlternativesSurviveAllFilters() {
        for (set in listOf("PLST", "MB1", "FMB1", "MB2", "PM20")) {
            val extra = variant(id = "retained", set = set, lang = "en").copy(collectorNumber = "M20-150")
            val result = evaluate(sharedArt().copy(variants = sharedArt().variants + extra), footer = fullFooter(),
                types = baseTypes + (set to "promo"))
            assertEquals(set, "RETAINED_FOOTER_POSSIBLE", result.reason)
        }
    }
    @Test fun missingOrUnsupportedSetMetadataBlocksNewFooterRoute() {
        assertEquals("UNSUPPORTED_SET_PROFILE", evaluate(sharedArt(), footer = fullFooter()).reason)
        assertEquals("UNSUPPORTED_SET_PROFILE", evaluate(sharedArt(), footer = fullFooter(), types = baseTypes + ("M19" to "commander")).reason)
    }
    @Test fun suffixAndAbsentNumberCannotSelectFirstPrinting() {
        assertEquals("FOOTER_NOT_IN_ART_CATALOGUE", evaluate(sharedArt(), footer = fullFooter(number = "150a"), types = baseTypes).reason)
        val onlyCode = StructuredPrintingRead(ScanReadState.READ, listOf(PrintedFooter("M20", null, "es")))
        assertEquals("MULTIPLE_SETS", evaluate(sharedArt(), footer = onlyCode, types = baseTypes).reason)
    }
    @Test fun duplicatedUuidAlternativesAndMissingPhysicalLanguageRemainManual() {
        val duplicate = sharedArt().variants + variant(id = "three", set = "M20")
        assertEquals("MULTIPLE_PRINTINGS", evaluate(top(duplicate), footer = fullFooter(), types = baseTypes).reason)
        assertEquals("LANGUAGE_NOT_IN_CATALOGUE", evaluate(sharedArt(), footer = fullFooter(), types = baseTypes,
            ev = evidence.copy(language = "pt")).reason)
    }
    @Test fun collectorCanSeparateTwoPrintingsInsideOneSetWithoutDefaultFinishFiltering() {
        val two = top(listOf(variant(set = "M20"), variant(id = "two", set = "M20").copy(collectorNumber = "200")))
        assertEquals("two", evaluate(two, footer = fullFooter(number = "200"), types = baseTypes).variant?.printingUuid)
        assertFalse(evaluate(two, types = baseTypes).canAutoAdd)
    }
    @Test fun proposedPracticeCardsHaveRealSharedArtworkAndDistinctFooterPrintings() {
        val index = ArtPrintingIndex.read(File("src/main/assets/art_printing_index.bin").inputStream())
        for (art in listOf("03510dd2-5e0b-4483-88fd-31aa41789a63", // Faerie Miscreant ORI/M20
            "195c2950-9ffa-4a77-a5e7-5b3978cefb43", // Sure Strike BFZ/M19/M21
            "4fc53a41-0bd7-4055-b6c1-7ff69175da71")) { // Mind Rot KLD/M19/M20/M21
            val variants = index.variants(art)
            assertTrue(variants.map { it.set.code }.distinct().size > 1)
            val spanish = variants.filter { it.languageCode == "es" }
            val name = spanish.first().cardName
            for (target in spanish) {
                val result = evaluate(RulesAutoAddPolicy.Artwork(art, name, 4, 8, variants), names = listOf(name),
                    footer = fullFooter(target.set.code, target.collectorNumber),
                    types = variants.associate { it.set.code to if (it.set.code.startsWith("M")) "core" else "expansion" })
                assertEquals("STRUCTURED_FOOTER", result.reason)
                assertEquals(target.printingUuid, result.variant?.printingUuid)
            }
        }
    }
    @Test fun titleLanguageSelectsPortugueseButDoesNotBreakExistingArtOrCatalogGates() {
        val title = TitleLanguageIndex.Evidence(listOf(TitleLanguageIndex.Match("Nome", "Card", "Nome", "pt")))
        val input = RulesScanPolicy.Input(listOf("Card"), emptyList(), emptyList(), unreadable, titleLanguage = title)
        val read = RulesScanPolicy.evaluate(input)
        val art = top(listOf(variant(lang = "en"), variant(lang = "pt")))
        assertEquals("pt", evaluate(art, ev = read).variant?.languageCode)
        assertFalse(evaluate(art.copy(phash = 20), ev = read).canAutoAdd)
        assertFalse(evaluate(top(listOf(variant(lang = "en"))), ev = read).canAutoAdd)
        val conflict = RulesScanPolicy.evaluate(input.copy(footer = StructuredPrintingRead(ScanReadState.READ,
            listOf(PrintedFooter("SPG", "150", "en")))))
        assertFalse(evaluate(art, ev = conflict).canAutoAdd)
    }
    @Test fun historicalNumeratorAgreementNeverResolvesMultiplePrintingsAutomatically() {
        val historical = HistoricalFooterEvidence.read(listOf(
            PrintingOcrLine(0, "©2003 Wizards 150/180", .1f, .94f, .9f, .96f),
            PrintingOcrLine(3, "©2003 Wizards 150/181", .1f, .94f, .9f, .96f)))
        assertEquals(ScanReadState.READ, historical.collectorNumbers.state)
        assertEquals("MULTIPLE_PRINTINGS", evaluate(top(listOf(variant(), variant(id = "two"))),
            footer = StructuredPrintingRead(ScanReadState.PARTIAL, historical = historical)).reason)
    }
}
