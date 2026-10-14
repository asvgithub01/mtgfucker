package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TriplePerformancePolicyTest {
    @Test fun resumingWithLastCardStillVisibleDoesNotCreateAnotherCopy() {
        val a=TripleRecognitionArbiter();val gate=TripleEncounterGate()
        a.restoreCurrent("fateshaper","EdScan");gate.restoreLastEvent(33,"fateshaper")
        assertFalse(a.observe("fateshaper","OCR",false,0,0).newEncounter)
        val same=a.observe("fateshaper","OCR",false,700,700)
        assertTrue(same.accepted);assertFalse(same.newEncounter)
        assertEquals(33L,gate.observe("fateshaper"))
    }
    @Test fun differentFirstCardAfterResumeIsANewEncounter() {
        val a=TripleRecognitionArbiter();a.restoreCurrent("old","EdScan")
        a.observe("next","OCR",false,0,0)
        assertTrue(a.observe("next","OCR",false,700,700).newEncounter)
    }
    @Test fun slowConsecutiveFramesCanConfirmOnlyAfterFreshVisualValidation() {
        val evidence=TripleVisualConfirmation()
        val art=floatArrayOf(-.3f,.3f)
        assertTrue(TripleSlowFramePolicy.matches(art,art,0,9000))
        assertFalse(evidence.observe("fateshaper",.74f,.39f,9000,0).reliable)
        assertTrue(TripleSlowFramePolicy.matches(art,art,9300,18000))
        assertTrue(evidence.observe("fateshaper",.74f,.39f,18000,9300).reliable)
    }
    @Test fun freshValidationRejectsMovedCardBlankAndExpiredWork() {
        val art=floatArrayOf(-.3f,.3f)
        assertFalse(TripleSlowFramePolicy.matches(art,floatArrayOf(.3f,-.3f),0,9000))
        assertFalse(TripleSlowFramePolicy.matches(art,null,0,9000))
        assertFalse(TripleSlowFramePolicy.matches(art,art,0,16000))
        assertFalse(TripleSlowFramePolicy.matches(art,floatArrayOf(Float.NaN,0f),0,9000))
    }
    @Test fun inactivityStillBreaksConfirmationDespiteSlowPipelineSupport() {
        val evidence=TripleVisualConfirmation()
        evidence.observe("a",.75f,.4f,9000,0)
        assertFalse(evidence.observe("a",.75f,.4f,26000,16000).reliable)
    }
    @Test fun arAndStrongEdScanHaveSamePersistedEvidenceButOcrAndConflictDoNot() {
        assertEquals(TriplePersistencePolicy.reviewSource("AR",false),TriplePersistencePolicy.reviewSource("EdScan",false))
        assertEquals("ocr",TriplePersistencePolicy.reviewSource("EdScan",true))
        assertEquals("ocr",TriplePersistencePolicy.reviewSource("AR",false,true))
        assertEquals("ocr",TriplePersistencePolicy.reviewSource("OCR",false))
    }
    @Test fun totalsRetainOlderEncountersAndReplaceQuantitiesRatherThanAddingAgain() {
        val totals=TripleSessionTotals()
        repeat(34) { totals.update(it.toLong(),1,1.25,"EUR") }
        assertEquals(42.5,totals.total { amount,_->amount },0.001)
        totals.update(0,3,1.25,"EUR")
        assertEquals(45.0,totals.total { amount,_->amount },0.001)
        totals.update(0,0,1.25,"EUR")
        assertEquals(41.25,totals.total { amount,_->amount },0.001)
    }
    @Test fun totalsKeepCurrenciesSeparateUntilConversionAndShowMissingPrices() {
        val totals=TripleSessionTotals()
        totals.update(1,2,10.0,"USD");totals.update(2,1,5.0,"EUR");totals.update(3,3,null,"EUR")
        assertEquals(23.0,totals.total { amount,currency -> if(currency=="USD") amount*.9 else amount },0.001)
        assertEquals(3,totals.pending)
        totals.update(3,3,2.0,"EUR");assertEquals(0,totals.pending)
    }
}
