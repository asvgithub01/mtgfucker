package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleIdentityConfirmationTest {
    private fun candidates(name:String="cyclopean mummy",flip:Boolean=false)=listOf(
        EdScanFastIdentity.Candidate(if(flip) "b" else "a",.86715776f,name,"4ed","1995"),
        EdScanFastIdentity.Candidate(if(flip) "a" else "b",.8627179f,name,"plst","2020"),
        EdScanFastIdentity.Candidate("other",.63595235f,"other card"))
    @Test fun sonyMummyConfirmsIdentityDespitePrintingTie() {
        val p=TripleIdentityConfirmation()
        assertFalse(p.observe(candidates(),false,1000).confirmation.reliable)
        val r=p.observe(candidates(),false,2000)
        assertTrue(r.confirmation.reliable);assertFalse(r.printingConfirmed)
        assertEquals("cyclopean mummy",r.evidence.identity)
    }
    @Test fun sonyV6ManamorphoseConfirmsWithoutCertifyingPrinting() {
        // Sony v14: consecutive ART frames 1792179478565 / 1792179480825.
        // Printing runner rejected the card; lane diagnostics retain the distinct-identity runner.
        fun frame(top:Float,printingRunner:Float,other:Float)=listOf(
            EdScanFastIdentity.Candidate("mma",top,"manamorphose","mma","2013-06-07"),
            EdScanFastIdentity.Candidate("reprint",printingRunner,"manamorphose"),
            EdScanFastIdentity.Candidate("other",other,"different card"))
        val p=TripleIdentityConfirmation()
        assertFalse(p.observe(frame(.9201401f,.906795f,.7021289f),false,1000).confirmation.reliable)
        val r=p.observe(frame(.90047437f,.8741614f,.6610686f),false,3277)
        assertTrue(r.confirmation.reliable)
        assertFalse(r.printingConfirmed)
        assertEquals("manamorphose",r.evidence.identity)
    }
    @Test fun printingLeaderMayChangeWithoutResettingSameIdentity() {
        val p=TripleIdentityConfirmation();p.observe(candidates(),true,1000)
        val r=p.observe(candidates(flip=true),true,2000)
        assertTrue(r.confirmation.reliable);assertFalse(r.printingConfirmed)
    }
    @Test fun differentIdentitiesNeverCombineVotes() {
        val p=TripleIdentityConfirmation();p.observe(candidates(),false,1000)
        assertFalse(p.observe(candidates("frozen shade"),false,2000).confirmation.reliable)
    }
    @Test fun onlyWholeCardCanCertifySeparatedPrinting() {
        val hits=listOf(EdScanFastIdentity.Candidate("a",.9f,"mummy"),EdScanFastIdentity.Candidate("b",.5f,"other"))
        val p=TripleIdentityConfirmation();p.observe(hits,false,1000)
        assertFalse(p.observe(hits,false,2000).printingConfirmed)
        assertFalse(p.observe(hits,true,3000).printingConfirmed)
        assertTrue(p.observe(hits,true,4000).printingConfirmed)
    }
    @Test fun unknownRunnerAndWeakScoreCannotAuthorizeIdentity() {
        for(hits in listOf(candidates().take(2),candidates().map { it.copy(score=it.score-.3f) })) {
            val p=TripleIdentityConfirmation();p.observe(hits,false,1000)
            assertFalse(p.observe(hits,false,2000).confirmation.reliable)
        }
    }
    @Test fun resetConflictAndRepeatedTimestampNeverReuseVotes() {
        val p=TripleIdentityConfirmation();p.observe(candidates(),false,1000)
        assertFalse(p.observe(candidates(),false,1000).confirmation.reliable)
        p.reset();assertFalse(p.observe(candidates(),false,2000).confirmation.reliable)
        p.observe(emptyList(),false,3000)
        assertFalse(p.observe(candidates(),false,4000).confirmation.reliable)
    }
    @Test fun provisionalIdentityStillPassesIndependentArAndFreshnessGuards() {
        val arbiter=TripleRecognitionArbiter()
        assertFalse(arbiter.observe("old","AR",false,1000,1000,requireIndependentAr=true).accepted)
        assertTrue(arbiter.observe("mummy","EdScan",true,2000,2000,requireIndependentAr=true).accepted)
        assertFalse(arbiter.observe("wrong","AR",false,3000,3000,requireIndependentAr=true).accepted)
        assertFalse(arbiter.observe("wrong","EdScan",true,1000,5000,requireIndependentAr=true).accepted)
    }
}
