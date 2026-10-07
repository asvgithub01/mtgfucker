package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class TriplePrintingLanguagePolicyTest {
    @Test fun onlyReliableVisualPrintingIsEligible() {
        for(source in listOf("AR","EdScan")) {
            assertTrue(TriplePrintingLanguagePolicy.eligible(source,false,"id","CHR","3"))
            assertFalse(TriplePrintingLanguagePolicy.eligible(source,true,"id","CHR","3"))
        }
        assertFalse(TriplePrintingLanguagePolicy.eligible("OCR",false,"id","CHR","3"))
        assertFalse(TriplePrintingLanguagePolicy.eligible("AR",false,"id","CHR",""))
    }
    @Test fun completeSingleLanguageOnly() {
        assertEquals("en",TriplePrintingLanguagePolicy.single(listOf("en","en"),true))
        assertNull(TriplePrintingLanguagePolicy.single(listOf("en"),false))
        assertNull(TriplePrintingLanguagePolicy.single(listOf("en","ja"),true))
        assertNull(TriplePrintingLanguagePolicy.single(emptyList(),true))
        assertNull(TriplePrintingLanguagePolicy.single(listOf("en",""),true))
        assertNull(TriplePrintingLanguagePolicy.single(listOf("und"),true))
    }
    @Test fun contraryOcrQuestionsEditionButUnknownDoesNot() {
        assertTrue(TriplePrintingLanguagePolicy.conflicts("en","pt"))
        assertFalse(TriplePrintingLanguagePolicy.conflicts("en","en"))
        assertFalse(TriplePrintingLanguagePolicy.conflicts("en",null))
        assertFalse(TriplePrintingLanguagePolicy.conflicts(null,"pt"))
    }
}
