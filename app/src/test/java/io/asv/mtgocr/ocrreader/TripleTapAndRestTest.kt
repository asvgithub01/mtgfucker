package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleTapAndRestTest {
    @Test fun soloArtAndFreshRevalidationDoNotPayAdaptiveWait() {
        for(ms in listOf(250L,1500L,2100L,7000L)) {
            assertEquals(150L,TripleScanPolicy.frameRestMs(ms,soloArt=true))
            assertEquals(150L,TripleScanPolicy.frameRestMs(ms,revalidating=true))
        }
        assertEquals(650L,TripleScanPolicy.frameRestMs(1500))
        assertEquals(150L,TripleScanPolicy.frameRestMs(1500,confirming=true))
    }
    @Test fun revalidationGoesFirstAndThenOcrGetsItsTurn() {
        val s=TripleFrameScheduler()
        assertEquals(TripleFrameScheduler.Lane.EDSCAN,s.next(1000,true,true,false,true))
        assertEquals(TripleFrameScheduler.Lane.OCR,s.next(1200,true,true,false,true))
        assertEquals(TripleFrameScheduler.Lane.EDSCAN,s.next(1400,true,false,false))
    }
    @Test fun tapCreatesNewEncounterForIdenticalCardButRejectsOldCapture() {
        val a=TripleRecognitionArbiter()
        assertTrue(a.observe("mummy","EdScan",true,1000,1100).newEncounter)
        a.beginManualScan(2000)
        assertFalse(a.observe("mummy","EdScan",true,1999,2100).accepted)
        assertFalse(a.observe("mummy","EdScan",false,2100,2200).accepted)
        assertTrue(a.observe("mummy","EdScan",true,2300,2400).newEncounter)
        val gate=TripleEncounterGate()
        assertEquals(1L,gate.observe("mummy"));gate.nextEncounter()
        assertEquals(2L,gate.observe("mummy"))
    }
    @Test fun tapDoesNotAuthorizeOneVisualVoteOrUnverifiedAr() {
        val a=TripleRecognitionArbiter();a.beginManualScan(1000)
        assertFalse(a.observe("mummy","AR",false,1200,1200,false,true).accepted)
        val p=TripleIdentityConfirmation()
        val hits=listOf(EdScanFastIdentity.Candidate("a",.8f,"mummy"),EdScanFastIdentity.Candidate("b",.5f,"other"))
        assertFalse(p.observe(hits,true,1300).confirmation.reliable)
        assertTrue(p.observe(hits,true,1500).confirmation.reliable)
    }
}
