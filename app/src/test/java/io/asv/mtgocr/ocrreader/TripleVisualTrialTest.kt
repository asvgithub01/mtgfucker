package io.asv.mtgocr.ocrreader

import io.asv.collectorvision.FastRegion
import org.junit.Assert.*
import org.junit.Test

class TripleVisualTrialTest {
    private fun hits(score:Float=.72f,runner:Float=.50f)=listOf(
        EdScanFastIdentity.Candidate("a",score,"mummy"),
        EdScanFastIdentity.Candidate("b",runner,"other"))
    @Test fun oneVoteConfirmsIdentityButNotPrintingAndDefaultStillNeedsTwo() {
        val trial=TripleIdentityConfirmation(true)
        val first=trial.observe(hits(),true,1000)
        assertTrue(first.confirmation.reliable);assertFalse(first.printingConfirmed)
        assertTrue(trial.observe(hits(),true,2000).printingConfirmed)
        assertFalse(TripleIdentityConfirmation().observe(hits(),true,1000).confirmation.reliable)
        assertFalse(EdScanFastPolicy().observe(true,"a",.72f,.5f,1000,1200).confirmed)
    }
    @Test fun singleVoteDoesNotRelaxScoreMarginRunnerOrTimestamp() {
        val trial=TripleIdentityConfirmation(true)
        assertFalse(trial.observe(hits(.69f),true,1000).confirmation.reliable)
        assertFalse(trial.observe(hits(.72f,.70f),true,2000).confirmation.reliable)
        assertFalse(trial.observe(hits().take(1),true,3000).confirmation.reliable)
        assertTrue(trial.observe(hits(),false,4000).confirmation.reliable)
        assertFalse(trial.observe(hits(),false,4000).confirmation.reliable)
        assertFalse(trial.observe(emptyList(),false,5000).confirmation.reliable)
    }
    @Test fun mummyTieSingleVoteLeavesEditionPendingAndAvoidsPlst() {
        val result=TripleIdentityConfirmation(true).observe(listOf(
            EdScanFastIdentity.Candidate("plst",.86715776f,"cyclopean mummy","plst","2020"),
            EdScanFastIdentity.Candidate("4ed",.8627179f,"cyclopean mummy","4ed","1995"),
            EdScanFastIdentity.Candidate("other",.63595235f,"different")),false,1000)
        assertTrue(result.confirmation.reliable);assertFalse(result.printingConfirmed)
        assertEquals("4ed",result.printing)
    }
    private fun reading(reason:String)=EdScanFastPolicy.Reading(reason=="confirmed",reason,0)
    @Test fun cropOnlyAfterTwoWeakReadsAndNoDetectionResetsToFull() {
        val p=TripleCropTrialPolicy(true)
        assertEquals(FastRegion.FULL,p.region)
        assertFalse(p.observe(true,reading("score")))
        assertFalse(p.observe(true,reading("out_of_order")))
        assertTrue(p.observe(true,reading("margin")))
        assertEquals(FastRegion.ART,p.region)
        assertFalse(p.observe(true,reading("score")))
        assertTrue(p.observe(true,reading("score")))
        assertEquals(FastRegion.UPPER,p.region)
        assertTrue(p.observe(false,reading("no_detection")))
        assertEquals(FastRegion.FULL,p.region)
    }
    @Test fun strongFirstVoteNeverSwitchesProfileAndDefaultIsUnchanged() {
        val p=TripleCropTrialPolicy(true)
        p.observe(true,reading("score"));p.observe(true,reading("readings"))
        assertFalse(p.observe(true,reading("score")))
        p.observe(true,reading("confirmed"))
        assertEquals(FastRegion.FULL,p.region)
        assertEquals(FastRegion.ART,TripleCropTrialPolicy().region)
    }
}
