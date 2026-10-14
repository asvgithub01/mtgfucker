package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

/** Replay the failure mechanisms seen on Sony, without requiring a person or a camera. */
class TripleLivenessRegressionTest {
    @Test fun loyalTutorDoesNotRequireAnExactPrintingAgreementOrOldPhysicalFingerprint() {
        val a=TripleRecognitionArbiter();a.observe("fateshaper","EdScan",true,0,0)
        assertFalse(a.observe("loyal tutor","AR",false,1000,1000).accepted)
        a.observe("loyal tutor","EdScan",false,1100,1300) // different printing, weak .67
        val found=a.observe("loyal tutor","AR",false,1700,1700)
        assertTrue(found.accepted);assertTrue(found.newEncounter)
    }
    @Test fun arOnlyNextCardGetsAnotherRowNotAnOverwriteThenPhysicalConfirmationDeadlock() {
        val a=TripleRecognitionArbiter()
        a.observe("first","AR",false,0,0);assertTrue(a.observe("first","AR",false,600,600).newEncounter)
        a.observe("next","AR",false,1600,1600)
        assertTrue(a.observe("next","AR",false,2300,2300).newEncounter)
        a.frame(floatArrayOf(-.3f,.3f),2400);a.frame(floatArrayOf(.3f,-.3f),2500);a.frame(floatArrayOf(.3f,-.3f),3000)
        assertTrue(a.observe("next","AR",false,3100,3100).accepted)
        assertFalse(a.observe("next","AR",false,3200,3200).newEncounter)
    }
    @Test fun disablingEdScanOrOcrClearsTheirVotesWithoutReaddingTheLastCard() {
        for(previous in listOf("EdScan","OCR")) {
            val a=TripleRecognitionArbiter()
            a.observe("old",previous,true,0,0);a.observe("old",previous,true,700,700)
            a.resetPending()
            assertFalse(a.observe("old","AR",false,900,900).newEncounter)
            a.observe("new","AR",false,2000,2000)
            assertTrue(a.observe("new","AR",false,2700,2700).newEncounter)
        }
    }
    @Test fun ocrProcessingEveryFourSecondsCanConvergeButStaleCapturesCannotVote() {
        val a=TripleRecognitionArbiter()
        assertFalse(a.observe("loyal tutor","OCR",false,1000,2500).accepted)
        assertTrue(a.observe("loyal tutor","OCR",false,5300,6800).newEncounter)
        assertEquals("stale",a.observe("different","OCR",false,6000,9000).reason)
    }
    @Test fun allSevenMotorCombinationsCanScan500AlternatingCardsWithoutLosingRows() {
        for(mask in 1..7) {
            val a=TripleRecognitionArbiter();var copies=0
            repeat(500) { index ->
                val name=if(index%2==0) "loyal tutor" else "danitha sword of hope"
                val t=index*4000L
                if(mask and 1!=0) { if(a.observe(name,"AR",false,t,t).newEncounter) copies++;if(a.observe(name,"AR",false,t+600,t+600).newEncounter) copies++ }
                if(mask and 2!=0) { if(a.observe(name,"EdScan",true,t+800,t+900).newEncounter) copies++ }
                if(mask and 4!=0) { if(a.observe(name,"OCR",false,t+1100,t+1200).newEncounter) copies++;if(a.observe(name,"OCR",false,t+1800,t+1900).newEncounter) copies++ }
                if(index%17==0) a.resetPending()
            }
            assertEquals("motor mask=$mask",500,copies)
        }
    }
    @Test fun rapidAlternationAndPrintingVariantsCannotMake49CopiesIn500ms() {
        val a=TripleRecognitionArbiter();var copies=0
        repeat(49) { i->if(a.observe("card${i%2}","EdScan",true,i*10L,i*10L).newEncounter) copies++ }
        assertEquals(1,copies)
        val same=TripleRecognitionArbiter();same.observe("loyal tutor","EdScan",true,0,0)
        repeat(100) { assertFalse(same.observe("loyal tutor","EdScan",true,1000+it*1000L,1000+it*1000L).newEncounter) }
    }
    @Test fun fixedScopeRejectsKnownOutOfScopeForEveryMotorIncludingSingleSetPackage() {
        assertTrue(TripleScanScopePolicy.allows(setOf("FRA"),"fra"))
        assertFalse(TripleScanScopePolicy.allows(setOf("FRA"),"FRC"))
        assertFalse(TripleScanScopePolicy.allows(setOf("FRA","FRC"),"SPG"))
        assertTrue(TripleScanScopePolicy.allows(setOf("FRA","FRC"),"FRC"))
        assertTrue(TripleScanScopePolicy.allows(setOf("FRA"),"")) // unresolved, not silently assigned globally
    }
}
