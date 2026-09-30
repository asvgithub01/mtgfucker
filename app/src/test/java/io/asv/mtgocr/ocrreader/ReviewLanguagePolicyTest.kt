package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class ReviewLanguagePolicyTest {
    @Test fun selectedEditionDefaultsToItsOnlyLanguage() {
        assertEquals("ja",ReviewLanguagePolicy.forSelectedEdition("",listOf("ja","ja"),listOf("en")))
    }
    @Test fun selectedEditionUsesSupportedOcrWhenSeveralLanguagesExist() {
        assertEquals("pt",ReviewLanguagePolicy.forSelectedEdition("",listOf("en","pt"),listOf("pt","pt")))
    }
    @Test fun selectedEditionNeverOverwritesValidSavedLanguage() {
        assertEquals("en",ReviewLanguagePolicy.forSelectedEdition("en",listOf("en","pt"),listOf("pt")))
    }
    @Test fun selectedEditionLeavesUnsupportedOrAmbiguousProposalPending() {
        assertEquals("",ReviewLanguagePolicy.forSelectedEdition("",listOf("en","pt"),listOf("es")))
        assertEquals("",ReviewLanguagePolicy.forSelectedEdition("",listOf("en","pt"),listOf("en","pt")))
        assertEquals("",ReviewLanguagePolicy.forSelectedEdition("",listOf("en","pt"),emptyList()))
        assertEquals("",ReviewLanguagePolicy.forSelectedEdition("",emptyList(),listOf("en")))
    }
    @Test fun changingToEditionWithoutOldLanguageUsesNewEditionRules() {
        assertEquals("ja",ReviewLanguagePolicy.forSelectedEdition("es",listOf("ja"),emptyList()))
        assertEquals("pt",ReviewLanguagePolicy.forSelectedEdition("es",listOf("en","pt"),listOf("pt")))
    }

    @Test fun selectedPrintingAllowsLanguageWithoutVisualConfirmation() {
        assertTrue(ReviewLanguagePolicy.hasSelectedPrinting("uuid","AER","142"))
        assertFalse(ReviewLanguagePolicy.hasSelectedPrinting("","AER","142"))
        assertFalse(ReviewLanguagePolicy.hasSelectedPrinting("uuid",null,"142"))
        assertFalse(ReviewLanguagePolicy.hasSelectedPrinting("uuid","AER",""))
    }
    @Test fun presetRequiresUniqueProposalAndUnknownSavedLanguage() {
        assertEquals("en",ReviewLanguagePolicy.proposedLanguage("",listOf("en","en",""),false))
        assertNull(ReviewLanguagePolicy.proposedLanguage("es",listOf("en"),false))
        assertNull(ReviewLanguagePolicy.proposedLanguage("",listOf("en","pt"),false))
        assertNull(ReviewLanguagePolicy.proposedLanguage("",listOf("en"),true))
        assertNull(ReviewLanguagePolicy.proposedLanguage("",emptyList(),false))
    }

    @Test fun preservesAvailableOrUnknownLanguage() {
        assertEquals("pt",ReviewLanguagePolicy.forEdition("pt",listOf("en","pt")))
        assertEquals("",ReviewLanguagePolicy.forEdition("",listOf("en","ja")))
    }
    @Test fun fallbackIsAvailableAndDeterministic() {
        assertEquals("en",ReviewLanguagePolicy.forEdition("es",listOf("ja","en")))
        assertEquals("ja",ReviewLanguagePolicy.forEdition("es",listOf("ja")))
        assertNull(ReviewLanguagePolicy.forEdition("es",emptyList()))
    }
}
