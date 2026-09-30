package io.asv.mtgocr.ocrreader
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test
class ReviewResolutionPolicyTest {
    private fun evidence(visual: Boolean=true)=ScanReviewStore.Entry("key",CardInfo("Skycat","","","","1"),"edscan","conflict","en","","",1,visual=visual,titleState=if(visual) "conflict" else "")
    @Test fun languageDraftCannotCloseUnresolvedIdentity() {
        assertTrue(ReviewResolutionPolicy.identityPending(listOf(evidence()),false,false))
        assertFalse(ReviewResolutionPolicy.identityPending(listOf(evidence()),true,false))
    }
    @Test fun confirmationCannotInventAnOcrPrinting() {
        assertTrue(ReviewResolutionPolicy.identityPending(listOf(evidence()),true,true))
    }
    @Test fun languageConflictNeedsExplicitChoiceButMatchingProposalDoesNot() {
        assertTrue(ReviewResolutionPolicy.languagePending(listOf(evidence(false)),"es",false))
        assertFalse(ReviewResolutionPolicy.languagePending(listOf(evidence(false)),"en",false))
        assertFalse(ReviewResolutionPolicy.languagePending(listOf(evidence(false)),"es",true))
        assertTrue(ReviewResolutionPolicy.languagePending(emptyList(),"",true))
    }
}
