package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class RulesScanPolicyTest {
    private val unreadable = StructuredPrintingRead(ScanReadState.UNREADABLE)
    private val printing = RulesScanPolicy.Printing("one", "Card", "WOE", "123")
    private fun footer(number: String? = "123", language: String = "en") =
        StructuredPrintingRead(ScanReadState.READ, listOf(PrintedFooter("WOE", number, language)))
    private fun input() = RulesScanPolicy.Input(listOf("Card"), listOf(RulesScanPolicy.Art("Card", 3, 3)), listOf(printing), footer())

    @Test fun matchingTupleProducesCandidateNeverExactOrAutoAdd() {
        val result = RulesScanPolicy.evaluate(input())
        assertEquals(RulesScanPolicy.Status.PRINTING_CANDIDATE, result.status)
        assertEquals(listOf("one"), result.printingUuids)
        assertEquals("ART_INDEX_ONLY", result.coverage)
        assertTrue(result.ruleIds.contains("P10"))
    }
    @Test fun artUniquenessDoesNotResolvePrinting() {
        assertEquals(RulesScanPolicy.Status.IDENTITY_ONLY, RulesScanPolicy.evaluate(input().copy(footer = unreadable)).status)
    }
    @Test fun missingIdentityOutsideTopKSurvives() {
        val result = RulesScanPolicy.evaluate(input().copy(art = emptyList(), printings = emptyList()))
        assertEquals(listOf("Card"), result.identities)
        assertEquals(RulesScanPolicy.Status.IDENTITY_ONLY, result.status)
    }
    @Test fun weakArtDoesNotCreateIdentity() {
        assertEquals(RulesScanPolicy.Status.NO_MATCH, RulesScanPolicy.evaluate(input().copy(names = emptyList(),
            art = listOf(RulesScanPolicy.Art("Card", 20, 30)))).status)
    }
    @Test fun contradictedStrongArtIsReviewConflict() {
        assertEquals(RulesScanPolicy.Status.CONFLICT, RulesScanPolicy.evaluate(input().copy(names = listOf("Another"))).status)
    }
    @Test fun multiplePrintingsRemainAlternatives() {
        val result = RulesScanPolicy.evaluate(input().copy(printings = listOf(printing, printing.copy(uuid = "two"))))
        assertEquals(RulesScanPolicy.Status.IDENTITY_ONLY, result.status)
        assertEquals(2, result.printingUuids.size)
    }
    @Test fun missingNumberNeverConfirmsPrinting() {
        assertEquals(RulesScanPolicy.Status.IDENTITY_ONLY, RulesScanPolicy.evaluate(input().copy(footer = footer(null))).status)
    }
    @Test fun languageContradictionIsNotDefaultedToEnglish() {
        val result = RulesScanPolicy.evaluate(input().copy(rulesLanguage = "es", rulesConfidence = .99f,
            rulesText = "Cuando esta criatura entre al campo de batalla"))
        assertEquals(RulesScanPolicy.Status.CONFLICT, result.status)
        assertEquals("", result.language)
        assertEquals(ScanReadState.CONFLICT, result.languageState)
    }
    @Test fun fallbackOrShortRulesDoNotInferLanguage() {
        for (confidence in listOf(0f, Float.NaN, 1f)) {
            val result = RulesScanPolicy.evaluate(input().copy(footer = unreadable, rulesLanguage = "en",
                rulesConfidence = confidence, rulesText = "Island"))
            assertEquals("", result.language)
        }
    }
    @Test fun reliableLongRulesAreLanguageEvidenceNotPrintingEvidence() {
        val result = RulesScanPolicy.evaluate(input().copy(footer = unreadable, rulesLanguage = "pt", rulesConfidence = .9f,
            rulesText = "Quando esta criatura entrar no campo de batalha"))
        assertEquals("pt", result.language)
        assertEquals(RulesScanPolicy.Status.IDENTITY_ONLY, result.status)
    }
    @Test fun suffixesStayDistinctAndZerosNormalize() {
        assertEquals("12a", RulesScanPolicy.collectorKey("0012A"))
        assertNotEquals(RulesScanPolicy.collectorKey("12O"), RulesScanPolicy.collectorKey("120"))
        assertEquals(emptyList<String>(), RulesScanPolicy.evaluate(input().copy(footer = footer("123a"))).printingUuids)
    }
    @Test fun footerConflictCannotResolveEvenUniqueArt() {
        assertEquals(RulesScanPolicy.Status.CONFLICT, RulesScanPolicy.evaluate(input().copy(
            footer = StructuredPrintingRead(ScanReadState.CONFLICT))).status)
    }
    private fun title(vararg languages: String) = TitleLanguageIndex.Evidence(languages.map {
        TitleLanguageIndex.Match("Título", "Card", "Título", it)
    })
    @Test fun exactTitleSuppliesMissingLanguageWithoutChangingPrintingCandidates() {
        val decision = RulesScanPolicy.evaluate(input().copy(footer = unreadable, titleLanguage = title("pt"),
            rulesLanguage = "pt", rulesConfidence = .79f, rulesText = "Texto demasiado corto"))
        assertEquals("pt", decision.language)
        assertEquals(listOf("one"), decision.printingUuids)
        assertTrue("L02" in decision.ruleIds)
    }
    @Test fun titleConflictsWithFooterOrStrongRulesBlockLanguage() {
        for (i in listOf(input().copy(titleLanguage = title("pt")), input().copy(footer = unreadable,
            titleLanguage = title("pt"), rulesLanguage = "es", rulesConfidence = .99f,
            rulesText = "Cuando esta criatura entre al campo de batalla"))) {
            val decision = RulesScanPolicy.evaluate(i)
            assertEquals(ScanReadState.CONFLICT, decision.languageState)
            assertEquals("", decision.language)
        }
    }
    @Test fun sharedTitleAloneDoesNotChooseSpanishAndRulesCanResolvePortuguese() {
        val base = input().copy(footer = unreadable, titleLanguage = title("es", "pt"))
        assertEquals("", RulesScanPolicy.evaluate(base).language)
        assertEquals("pt", RulesScanPolicy.evaluate(base.copy(rulesLanguage = "pt", rulesConfidence = .99f,
            rulesText = "Quando esta criatura entrar no campo de batalha")).language)
    }
}
