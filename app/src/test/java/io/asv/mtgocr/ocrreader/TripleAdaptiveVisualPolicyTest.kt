package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleAdaptiveVisualPolicyTest {
    @Test fun lyraModerateSamplesQualifyConditionallyButIncompleteComparisonCannotAuthorize() {
        val p=TripleAdaptiveVisualPolicy()
        val scores=listOf(.6232963f,.6094274f,.61966985f)
        val runners=listOf(.40154567f,.39704266f,.3808973f)
        for(i in scores.indices) {
            val result=p.observe("lyra",scores[i],runners[i],i*2250L,false)
            assertEquals(i==2,result.hypothetical)
            assertFalse(result.candidate)
            assertEquals("incomplete_comparison",result.reason)
        }
    }
    @Test fun hortimancerOneModerateThenSubMinimumDoesNotBecomeAutomaticRecognition() {
        val p=TripleAdaptiveVisualPolicy()
        for((i,score) in listOf(.724315f,.5480534f,.52722865f).withIndex())
            assertFalse(p.observe("hortimancer",score,score-.25f,i*2000L,true).candidate)
    }
    @Test fun lowScoresRequireBothWideMarginAndManyIndependentFrames() {
        val p=TripleAdaptiveVisualPolicy()
        repeat(4) { assertFalse(p.observe("a",.56f,.20f,it*1800L,true).candidate) }
        assertTrue(p.observe("a",.56f,.20f,7200,true).candidate)
        val q=TripleAdaptiveVisualPolicy()
        repeat(20) { assertFalse(q.observe("a",.56f,.40f,it*600L,true).candidate) }
    }
    @Test fun strongWideMarginCanBeSingleFrameOnlyInHypothesis() {
        assertTrue(TripleAdaptiveVisualPolicy().observe("a",.9f,.4f,0,true).candidate)
        assertFalse(TripleAdaptiveVisualPolicy().observe("a",.9f,.8f,0,true).candidate)
        assertFalse(TripleAdaptiveVisualPolicy().observe("a",.9f,null,0,true).candidate)
        assertFalse(TripleAdaptiveVisualPolicy().observe("a",Float.NaN,.4f,0,true).candidate)
    }
    @Test fun neutralDipDuplicateAndOtherIdentityNeverInventVotes() {
        val p=TripleAdaptiveVisualPolicy()
        p.observe("a",.62f,.4f,0,true)
        p.observe("a",.53f,.4f,1000,true)
        assertFalse(p.observe("a",.62f,.4f,2000,true).candidate)
        assertFalse(p.observe("a",.62f,.4f,2000,true).candidate)
        assertTrue(p.observe("a",.62f,.4f,4000,true).candidate)
        assertFalse(p.observe("b",.62f,.4f,5000,true).candidate)
        assertFalse(p.observe("a",.62f,.4f,6000,true).candidate)
        assertFalse(p.observe("a",.62f,.4f,13000,true).candidate)
    }
    @Test fun accumulatedVotesHaveBoundedAgeNotJustBoundedGap() {
        val p=TripleAdaptiveVisualPolicy()
        repeat(5) { assertFalse(p.observe("a",.56f,.2f,it*5000L,true).candidate) }
    }
    @Test fun acceptanceRegionIsMonotonicInScoreMarginAndReadings() {
        fun accepts(s:Float,m:Float,n:Int):Boolean {
            val p=TripleAdaptiveVisualPolicy();var accepted=false
            repeat(n) { accepted=p.observe("a",s,s-m,it*700L,true).candidate }
            return accepted
        }
        for(si in 55..95 step 5) for(mi in 5..40 step 5) for(n in 1..5) {
            val s=si/100f;val m=mi/100f
            if(accepts(s,m,n)) {
                assertTrue(accepts(s+.01f,m,n));assertTrue(accepts(s,m+.01f,n));assertTrue(accepts(s,m,n+1))
            }
        }
    }
    @Test fun indistinguishablePrintingRunnerIsNeverRemovedToManufactureAMargin() {
        val p=TripleAdaptiveVisualPolicy()
        repeat(10) { assertFalse(p.observe("same-name-variant-a",.80f,.79f,it*700L,true).candidate) }
    }
}
