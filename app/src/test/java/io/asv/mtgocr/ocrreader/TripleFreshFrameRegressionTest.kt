package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

/** Replay timing/coordination, NOT a physical recognition benchmark. */
class TripleFreshFrameRegressionTest {
    private val old=floatArrayOf(-.3f,.3f,-.3f,.3f)
    private val next=floatArrayOf(.3f,-.3f,.3f,-.3f)

    @Test fun ocrStageTimeoutNeverOverlapsModelsOrReleasesANewerLane() {
        var releases=0
        val gate=TripleOcrStageGate { releases++ }
        gate.beginName();gate.expire()
        assertEquals(0,releases) // OCR/language ML still running
        gate.endModels();assertEquals(1,releases)
        gate.endName();gate.expire();assertEquals(1,releases) // late callback, no second release
        val normal=TripleOcrStageGate { releases++ }
        normal.beginName();normal.endModels();assertEquals(1,releases)
        normal.endName();assertEquals(2,releases)
    }

    @Test fun shortestBottomEdgeDoesNotTurnUprightTitleUpsideDown() {
        val top=listOf(io.asv.collectorvision.Corner(.1f,.1f),io.asv.collectorvision.Corner(.9f,.1f),
            io.asv.collectorvision.Corner(.8f,.9f),io.asv.collectorvision.Corner(.2f,.9f))
        assertEquals(top,TripleOcrGeometry.upright(top))
        assertEquals(top,TripleOcrGeometry.upright(top.drop(2)+top.take(2)))
        assertEquals(emptyList<io.asv.collectorvision.Corner>(),TripleOcrGeometry.upright(emptyList()))
    }

    @Test fun lyraOcrSurvivesAppearanceBetweenItsTwoRealCaptures() {
        val a=TripleRecognitionArbiter()
        a.frame(old,2284280000)
        a.observe("previous","EdScan",true,2284280000,2284280000)
        val first=2284286658L;val second=2284291171L
        a.frame(next,first)
        assertEquals("stability",a.observe("lyra","OCR",false,first,first+2229).reason)
        a.frame(next,second) // v12 cleared the first OCR vote here
        assertEquals(1L,a.scene)
        val accepted=a.observe("lyra","OCR",false,second,second+2031)
        assertTrue(accepted.accepted);assertTrue(accepted.newEncounter)
        assertFalse(a.observe("lyra","AR",false,second+2100,second+2100).newEncounter)
    }
    @Test fun appearancePreservesHistoryButNeverAcceptsAnOldCallbackOrRearmsSameCard() {
        val a=TripleRecognitionArbiter()
        a.frame(old,0);a.observe("same","EdScan",true,0,0)
        a.observe("next","OCR",false,1000,1100)
        a.frame(next,1500);a.frame(next,2000)
        assertEquals("stale",a.observe("next","OCR",false,1900,2100).reason)
        assertFalse(a.observe("same","AR",false,2100,2100).newEncounter)
        a.missing(3000);a.missing(3700);a.missing(4500)
        assertFalse(a.observe("next","OCR",false,4600,4700).accepted) // absence clears votes
        assertTrue(a.observe("next","OCR",false,5300,5400).newEncounter)
    }
    @Test fun otherMotorFrequencyDoesNotResetOrMultiplyArOrOcrVotes() {
        val a=TripleRecognitionArbiter()
        a.observe("ar","AR",false,0,0)
        repeat(10) { a.observe("ocr","OCR",false,10L+it*40,10L+it*40) }
        assertTrue(a.observe("ar","AR",false,600,600).newEncounter)
        val b=TripleRecognitionArbiter()
        b.observe("ocr","OCR",false,0,0)
        repeat(10) { b.observe("weak","EdScan",false,10L+it*40,10L+it*40) }
        assertTrue(b.observe("ocr","OCR",false,700,700).newEncounter)
    }
    @Test fun hortimancerNeverReusesTheFrameThatSpent1620msInEdScan() {
        val scheduler=TripleFrameScheduler()
        assertEquals(TripleFrameScheduler.Lane.OCR,scheduler.next(0,true,true,false))
        assertEquals(TripleFrameScheduler.Lane.EDSCAN,scheduler.next(1500,true,true,false))
        assertEquals(TripleFrameScheduler.Lane.OCR,scheduler.next(3770,true,true,false))
        val a=TripleRecognitionArbiter()
        assertEquals("stale",a.observe("hortimancer","OCR",false,1500,1500+2893).reason)
        // A fresh capture can still expire; no timestamp rejuvenation or enlarged TTL.
        assertEquals("stability",a.observe("hortimancer","OCR",false,3770,3770+1273).reason)
        assertEquals("stale",a.observe("hortimancer","OCR",false,6000,8501).reason)
    }
    @Test fun schedulerServesBothSlowLanesRegardlessOfUrgencyOrPreviousScene() {
        val s=TripleFrameScheduler()
        assertEquals(TripleFrameScheduler.Lane.OCR,s.next(0,true,true,true))
        assertEquals(TripleFrameScheduler.Lane.EDSCAN,s.next(200,true,true,true))
        assertEquals(TripleFrameScheduler.Lane.OCR,s.next(1400,true,true,false))
        assertEquals(TripleFrameScheduler.Lane.EDSCAN,s.next(4000,true,true,false))
        assertEquals(TripleFrameScheduler.Lane.OCR,s.next(6000,true,true,false))
        s.reset()
        assertEquals(TripleFrameScheduler.Lane.OCR,s.next(6001,false,true,false))
    }
    @Test fun schedulerSupportsAllSevenMotorCombinationsAndChangesDuringWork() {
        for(mask in 1..7) {
            val s=TripleFrameScheduler();val lanes=mutableSetOf<TripleFrameScheduler.Lane>()
            repeat(8) { lanes+=s.next(it*2000L,mask and 2!=0,mask and 4!=0,false) }
            assertEquals("mask=$mask",mask and 2!=0,TripleFrameScheduler.Lane.EDSCAN in lanes)
            assertEquals("mask=$mask",mask and 4!=0,TripleFrameScheduler.Lane.OCR in lanes)
            s.reset() // generation change also discards Activity callbacks
            assertEquals(TripleFrameScheduler.Lane.IDLE,s.next(20000,false,false,false))
        }
    }
    @Test fun interleavedOcrDoesNotExpireEdScanButOldIndividualSamplesStillDo() {
        val v=TripleVisualConfirmation()
        assertFalse(v.observe("same",.74f,.46f,0).reliable)
        assertTrue(v.observe("same",.74f,.46f,4500).reliable)
        assertFalse(v.observe("same",.74f,.46f,11000).reliable)
        assertFalse(v.observe("same",.74f,.46f,10900).reliable)
        assertFalse(v.observe("same",.74f,.46f,11000).reliable)
    }
}
