package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class ReviewAnalysisPolicyTest {
    @Test fun edscanNeverNeedsArtEvenWhenExplicitlyReanalysed() {
        for(source in listOf("edscan","")) for(save in listOf(false,true)) {
            assertTrue(ReviewAnalysisPolicy.plan(source,save,false,true).languageOnly)
        }
    }
    @Test fun explicitRetryReadsOcrAgainInsteadOfCopyingTheOriginal() {
        assertFalse(ReviewAnalysisPolicy.plan("edscan",false,false,true).copyOriginalOcr)
        assertTrue(ReviewAnalysisPolicy.plan("edscan",true,false,true).copyOriginalOcr)
        assertFalse(ReviewAnalysisPolicy.plan("edscan",true,true,true).copyOriginalOcr)
        assertFalse(ReviewAnalysisPolicy.plan("edscan",true,false,false).copyOriginalOcr)
    }
    @Test fun ocrOriginStillRequiresArtAndNeverCopiesEdscanOriginal() {
        for(save in listOf(false,true)) {
            val plan=ReviewAnalysisPolicy.plan("ocr",save,false,true)
            assertFalse(plan.languageOnly)
            assertFalse(plan.copyOriginalOcr)
        }
    }
    @Test fun oldFullEdscanRetryOnlyDisplaysItsLanguageEvidence() {
        assertEquals(listOf("language:attempt"),ReviewAnalysisPolicy.evidenceAttempts("edscan","attempt",true))
        assertEquals(listOf(""),ReviewAnalysisPolicy.evidenceAttempts("edscan","attempt",false))
        assertEquals(listOf(""),ReviewAnalysisPolicy.evidenceAttempts("edscan",null,false))
    }
    @Test fun ocrOriginRetainsBothEvidenceAndOriginalKeys() {
        assertEquals(listOf("language:attempt","attempt"),ReviewAnalysisPolicy.evidenceAttempts("ocr","attempt",true))
        assertEquals(listOf("attempt"),ReviewAnalysisPolicy.evidenceAttempts("ocr","attempt",false))
        assertEquals(listOf(""),ReviewAnalysisPolicy.evidenceAttempts("ocr",null,false))
    }
}
