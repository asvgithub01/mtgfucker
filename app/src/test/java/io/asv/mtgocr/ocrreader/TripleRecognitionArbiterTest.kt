package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleRecognitionArbiterTest {
    private fun TripleRecognitionArbiter.read(id:String,source:String,at:Long,strong:Boolean=source=="EdScan")=
        observe(id,source,strong,at,at)

    @Test fun weakNextCardCanAdvancePhysicalSceneWithoutLoweringRecognitionThreshold() {
        val a=TripleRecognitionArbiter()
        val visual=TripleVisualConfirmation()
        val old=floatArrayOf(-.3f,.3f,-.3f,.3f)
        val next=floatArrayOf(.3f,-.3f,.3f,-.3f)
        a.frame(old,0)
        assertTrue(a.read("theorix annex","EdScan",0).newEncounter)
        for(t in listOf(1000L,1700L)) {
            val weak=visual.observe("konstrari annex",.55f,.32f,t)
            assertEquals("score",weak.reason)
            assertFalse(weak.reliable)
            // Same routing as the camera: forward the crop even when recognition is weak.
            a.frame(next,t)
            assertFalse(a.read("konstrari annex","EdScan",t,weak.reliable).accepted)
        }
        assertEquals(1L,a.scene)
        // Old AR callbacks cannot consume the new physical scene.
        a.read("theorix annex","AR",1700)
        assertFalse(a.read("theorix annex","AR",2400).newEncounter)
        assertFalse(a.read("konstrari annex","OCR",2400).accepted)
        val accepted=a.read("konstrari annex","OCR",3501) // old AR is no longer fresh
        assertTrue(accepted.accepted)
        assertTrue(accepted.newEncounter)
        // Repeated new-card results update the one encounter, not extra copies.
        a.read("konstrari annex","AR",3100)
        assertFalse(a.read("konstrari annex","AR",3800).newEncounter)
        assertFalse(a.read("konstrari annex","OCR",3800).newEncounter)
    }

    @Test fun weakRecognitionCannotDisplaceFreshCompetingVisualEvidence() {
        val a=TripleRecognitionArbiter()
        val same=floatArrayOf(-.3f,.3f)
        a.frame(same,0);a.read("old","EdScan",0)
        for(t in listOf(1000L,1700L,2400L)) {
            a.frame(same,t)
            assertFalse(a.read("new","EdScan",t,false).accepted)
            a.read("old","EdScan",t+1)
            assertFalse(a.read("new","AR",t+2).accepted)
            assertFalse(a.read("new","OCR",t+2).accepted)
        }
        assertEquals(0L,a.scene)
    }

    @Test fun arPreviewWaitsForStableIndependentObservations() {
        val a=TripleRecognitionArbiter()
        assertFalse(a.read("a","AR",0).accepted)
        repeat(20) { assertFalse(a.read("a","AR",0).accepted) }
        assertFalse(a.read("a","AR",599).accepted)
        assertTrue(a.read("a","AR",600).newEncounter)
        assertFalse(a.read("a","AR",700).newEncounter)
    }
    @Test fun replayEdScanArBurstCorrectsOneEncounterRatherThanAddingCopies() {
        val a=TripleRecognitionArbiter()
        a.read("wrong","AR",0)
        assertTrue(a.read("wrong","AR",600).newEncounter)
        val corrected=a.observe("right","EdScan",true,500,1000)
        assertTrue(corrected.accepted);assertFalse(corrected.newEncounter)
        for(t in 1000L..10000L step 1000) {
            assertFalse(a.read("right","EdScan",t).newEncounter)
            assertFalse(a.read("wrong","AR",t+27).accepted)
        }
    }
    @Test fun danithaConfirmedVisualIsNotVetoedByPreviousCardAuthority() {
        val a=TripleRecognitionArbiter()
        assertTrue(a.read("predictive preparations","EdScan",0).newEncounter)
        assertTrue(a.read("danitha sword of hope","EdScan",3000).newEncounter)
        assertFalse(a.read("danitha sword of hope","EdScan",4500).newEncounter)
        assertFalse(a.read("predictive preparations","AR",4501).accepted)
    }
    @Test fun stableAppearanceChangeAllowsDifferentCardWithoutTakingItOut() {
        val a=TripleRecognitionArbiter()
        val first=floatArrayOf(-.3f,.3f,-.3f,.3f)
        val second=floatArrayOf(.3f,-.3f,.3f,-.3f)
        a.frame(first,0);assertTrue(a.read("a","EdScan",0).newEncounter)
        a.frame(second,1000);assertEquals(0L,a.scene)
        a.frame(second,1400);assertEquals(1L,a.scene)
        assertTrue(a.read("b","EdScan",1400).newEncounter)
        assertFalse(a.read("a","AR",1500).accepted)
    }
    @Test fun transientOcclusionAndSmallAppearanceNoiseDoNotAddCopies() {
        val a=TripleRecognitionArbiter()
        val first=floatArrayOf(-.3f,.3f)
        a.frame(first,0);a.read("a","EdScan",0)
        a.frame(floatArrayOf(.3f,-.3f),1000)
        a.frame(first,1600)
        a.frame(floatArrayOf(-.28f,.28f),2200)
        assertEquals(0L,a.scene)
        assertFalse(a.read("a","EdScan",2200).newEncounter)
    }
    @Test fun sustainedAbsenceRearmsSameCopyButSingleMissDoesNot() {
        val a=TripleRecognitionArbiter();a.read("a","EdScan",0)
        a.missing(100);a.present();a.missing(1200)
        assertFalse(a.read("a","EdScan",1400).newEncounter)
        a.present();a.missing(2000);a.missing(2700);a.missing(3500)
        assertTrue(a.read("a","EdScan",3600).newEncounter)
    }
    @Test fun callbacksFromPreviousSceneAndOutOfOrderFramesCannotVote() {
        val a=TripleRecognitionArbiter();a.read("a","EdScan",0)
        a.missing(100);a.missing(800);a.missing(1600)
        assertEquals("stale",a.observe("a","EdScan",true,900,1700).reason)
        assertEquals("stale",a.observe("b","EdScan",true,1700,5000).reason)
        a.read("b","OCR",5000)
        assertEquals("out_of_order",a.observe("a","OCR",false,4900,5000).reason)
    }
    @Test fun resettingPendingStateDoesNotDuplicateCurrentCard() {
        val a=TripleRecognitionArbiter();a.read("a","EdScan",0)
        a.resetPending()
        assertFalse(a.read("a","EdScan",1000).newEncounter)
    }
    @Test fun weakEdScanAndSingleOcrCannotDisplaceAr() {
        val a=TripleRecognitionArbiter();a.read("a","AR",0);a.read("a","AR",600)
        assertFalse(a.read("b","EdScan",700,false).accepted)
        assertFalse(a.read("b","OCR",800).accepted)
    }
    @Test fun appearanceChangeDoesNotBypassMinimumCopyInterval() {
        val a=TripleRecognitionArbiter()
        a.frame(floatArrayOf(-.3f,.3f),0);a.read("a","EdScan",0)
        a.frame(floatArrayOf(.3f,-.3f),100);a.frame(floatArrayOf(.3f,-.3f),500)
        assertEquals("copy_interval",a.read("b","EdScan",500).reason)
        assertTrue(a.read("b","EdScan",600).newEncounter)
    }
    @Test fun appearanceNoiseOnSameIdentityIsNotAnIdenticalCopy() {
        val a=TripleRecognitionArbiter()
        a.frame(floatArrayOf(-.3f,.3f),0);a.read("a","EdScan",0)
        a.frame(floatArrayOf(.3f,-.3f),1000);a.frame(floatArrayOf(.3f,-.3f),1500)
        assertFalse(a.read("a","AR",1600).newEncounter)
        val same=a.read("a","EdScan",1700)
        assertTrue(same.accepted);assertFalse(same.newEncounter)
    }
    @Test fun oldArCannotConsumePhysicalChangeBeforeEdScan() {
        val a=TripleRecognitionArbiter()
        a.frame(floatArrayOf(-.3f,.3f),0);a.read("a","EdScan",0)
        a.frame(floatArrayOf(.3f,-.3f),1000);a.frame(floatArrayOf(.3f,-.3f),1500)
        a.read("a","AR",1500)
        assertFalse(a.read("a","AR",2200).newEncounter)
        assertTrue(a.read("b","EdScan",2300).newEncounter)
    }
}
