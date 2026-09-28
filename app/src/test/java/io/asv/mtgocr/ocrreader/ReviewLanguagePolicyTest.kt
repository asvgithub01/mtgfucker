package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class ReviewLanguagePolicyTest {
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
