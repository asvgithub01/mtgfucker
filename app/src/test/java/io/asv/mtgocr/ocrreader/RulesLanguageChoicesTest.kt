package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class RulesLanguageChoicesTest {
    @Test fun exactPrintingLanguagesReplaceGlobalList() {
        assertEquals(listOf("en", "pt"), RulesLanguageChoices.codes(listOf("en", "pt", "en"), false))
    }
    @Test fun observedLanguageOutsideCatalogueCannotBePreselected() {
        assertEquals(-1, RulesLanguageChoices.codes(listOf("en", "ja"), false).indexOf("pt"))
    }
    @Test fun foreignOnlyPrintingDoesNotManufactureEnglish() {
        assertEquals(listOf("pt"), RulesLanguageChoices.codes(listOf("pt"), false))
    }
    @Test fun failedLookupKeepsManualRecoveryInsteadOfPretendingCompleteCoverage() {
        assertTrue(RulesLanguageChoices.codes(emptyList(), true).containsAll(listOf("pt", "es", "en")))
    }
}
