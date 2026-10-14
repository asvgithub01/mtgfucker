package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test

class TripleArConsensusTest {
    private fun stable(id:String="uuid")=TripleArConsensus().apply { (1000L..1800L step 100).forEach { observe(id,it) } }
    @Test fun oneModerateFrameWithExactStableArCanConfirm() {
        assertTrue(stable().confirms("uuid",.61f,.4f,1200,1800))
    }
    @Test fun rejectsDifferentPrintingEvenIfTheNameWouldMatch() {
        assertFalse(stable().confirms("other-uuid",.9f,.4f,1200,1800))
    }
    @Test fun rejectsWeakAndAmbiguousAndNonFiniteResults() {
        for(pair in listOf(.59f to .4f,.65f to .6f,Float.NaN to .4f,.9f to Float.NaN))
            assertFalse(stable().confirms("uuid",pair.first,pair.second,1200,1800))
    }
    @Test fun rejectsExpiredFrameOrTrackingAndFrameFromPreviousCard() {
        assertFalse(stable().confirms("uuid",.7f,.4f,900,1800))
        assertFalse(stable().confirms("uuid",.7f,.4f,1200,2200))
        val a=stable();(1900L..4000L step 100).forEach { a.observe("uuid",it) }
        assertFalse(a.confirms("uuid",.7f,.4f,1200,4000))
    }
    @Test fun alternatingArOrLossOfTrackingDoesNotAccumulateVotes() {
        val a=stable();a.observe("other",1850);a.observe("uuid",1900)
        assertFalse(a.confirms("uuid",.7f,.4f,1900,1900))
        a.observe(null,1950);a.observe("uuid",2000)
        assertFalse(a.confirms("uuid",.7f,.4f,2000,2000))
        val b=stable();b.observe("uuid",2300)
        assertFalse(b.confirms("uuid",.7f,.4f,2300,2300))
    }
    @Test fun transitionDoesNotWaitForTwoFullAppearanceAnalyses() {
        val a=TripleRecognitionArbiter()
        a.observe("previous","EdScan",true,0,0)
        a.observe("next","AR",false,1000,1000)
        assertTrue(a.observe("next","AR",false,1700,1700).newEncounter)
        val d=a.observe("next","EdScan",true,1700,1800,pairedVisual=true)
        assertTrue(d.accepted);assertFalse(d.newEncounter)
        assertFalse(a.observe("next","EdScan",true,1900,1900,pairedVisual=true).newEncounter)
        assertFalse(a.observe("next","AR",false,2000,2000).newEncounter)
    }
    @Test fun newEditionOfSameCardIsNotAnotherCopyAndNoBurstIsAllowed() {
        val a=TripleRecognitionArbiter();a.observe("same name","EdScan",true,1000,1000)
        assertFalse(a.observe("same name","EdScan",true,1200,1200,pairedVisual=true).newEncounter)
        assertEquals("copy_interval",a.observe("other","EdScan",true,1400,1400,pairedVisual=true).reason)
    }
    @Test fun delayedCameraArCannotRestartVisualAuditAfterFreshDirectAr() {
        val a=TripleRecognitionArbiter();a.observe("card","EdScan",true,0,0)
        a.observe("card","AR",false,1500,1500)
        val late=a.observe("card","AR",false,1200,1600)
        assertEquals("out_of_order",late.reason)
        assertFalse(TripleScanPolicy.confirmationNeeded(false,a.needsConfirmation,late.reason))
        assertFalse(TripleScanPolicy.confirmationNeeded(false,true,"stale"))
    }
    @Test fun realContradictionStillRequestsAuditAndPendingWorkIsNotLost() {
        assertTrue(TripleScanPolicy.confirmationNeeded(false,true,"edscan_priority"))
        assertTrue(TripleScanPolicy.confirmationNeeded(true,true,"stale"))
    }
    @Test fun stalePairedResultCannotOpenScene() {
        val a=TripleRecognitionArbiter();a.observe("old","EdScan",true,0,0)
        assertEquals("stale",a.observe("new","EdScan",true,1000,4000,pairedVisual=true).reason)
        assertEquals(0L,a.scene)
    }
}
