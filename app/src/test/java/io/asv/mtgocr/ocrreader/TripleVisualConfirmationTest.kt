package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleVisualConfirmationTest {
    @Test fun requiresTwoIndividuallyStrongFrames() {
        val c=TripleVisualConfirmation()
        assertEquals("score",c.observe("a",.69f,.3f,0).reason)
        assertEquals("readings",c.observe("a",.8f,.4f,300).reason)
        val confirmed=c.observe("a",.8f,.4f,600)
        assertTrue(confirmed.reliable);assertEquals(600L,confirmed.elapsedMs)
    }
    @Test fun weakMarginAndMissingRunnerResetTheStreak() {
        val c=TripleVisualConfirmation()
        c.observe("a",.8f,.4f,0)
        assertEquals("margin",c.observe("a",.8f,.79f,300).reason)
        assertFalse(c.observe("a",.8f,.4f,600).reliable)
        assertEquals("no_runner",c.observe("a",.8f,null,900).reason)
        assertFalse(c.observe("a",.8f,.4f,1200).reliable)
    }
    @Test fun duplicateFrameDifferentIdentityGapAndResetNeverSupplySecondVote() {
        val c=TripleVisualConfirmation()
        c.observe("a",.8f,.4f,0)
        assertFalse(c.observe("a",.8f,.4f,0).reliable)
        assertFalse(c.observe("b",.8f,.4f,300).reliable)
        assertFalse(c.observe("b",.8f,.4f,7000).reliable)
        c.reset();assertFalse(c.observe("b",.8f,.4f,7300).reliable)
    }
    @Test fun nonFiniteScoresAreNeverAccepted() {
        val c=TripleVisualConfirmation()
        for(score in listOf(Float.NaN,Float.POSITIVE_INFINITY)) assertFalse(c.observe("a",score,.4f,0).reliable)
        assertFalse(c.observe("a",.8f,Float.NaN,100).reliable)
    }
    @Test fun retryLatencyDoesNotIncludeTimeAlreadyConfirmed() {
        val c=TripleVisualConfirmation()
        c.observe("a",.8f,.4f,0);c.observe("a",.8f,.4f,300)
        c.observe("a",.8f,.4f,600);c.observe("a",.8f,.4f,900)
        c.observe("a",.6f,.4f,1200);c.observe("a",.8f,.4f,1500)
        assertEquals(600L,c.observe("a",.8f,.4f,1800).elapsedMs)
    }
}
