package io.asv.mtgocr.ocrreader

import io.asv.collectorvision.FastRegion
import org.junit.Assert.*
import org.junit.Test

class EdScanFastAutoPolicyTest {
    private fun reading(reason:String)=EdScanFastPolicy.Reading(reason=="confirmed",reason,0)
    @Test fun failuresCycleWithoutRequiringRemoval() {
        val p=EdScanFastAutoPolicy()
        repeat(3) {
            for(next in listOf(FastRegion.UPPER,FastRegion.ART)) {
                assertFalse(p.observe(true,reading("score")))
                assertTrue(p.observe(true,reading("margin")))
                assertEquals(next,p.region)
            }
        }
    }
    @Test fun confirmationStaysButNewWeakCardCanEscapeEveryProfile() {
        val p=EdScanFastAutoPolicy()
        for(next in listOf(FastRegion.UPPER,FastRegion.ART)) {
            repeat(5) { assertFalse(p.observe(true,reading("confirmed"))) }
            assertFalse(p.observe(true,reading("score")))
            assertTrue(p.observe(true,reading("score")))
            assertEquals(next,p.region)
        }
    }
    @Test fun unstableAndStaleReadingsAreBoundedInEveryProfile() {
        val p=EdScanFastAutoPolicy()
        for(next in listOf(FastRegion.UPPER,FastRegion.ART)) {
            repeat(3) {
                assertFalse(p.observe(true,reading("readings")))
                assertFalse(p.observe(true,reading("stale")))
                assertFalse(p.observe(true,reading("out_of_order")))
            }
            assertTrue(p.observe(true,reading("readings")))
            assertEquals(next,p.region)
        }
    }
    @Test fun confidenceVotesNeverCrossProfileBoundary() {
        val p=EdScanFastAutoPolicy();val confidence=EdScanFastPolicy()
        fun frame(score:Float,at:Long):EdScanFastPolicy.Reading {
            val r=confidence.observe(true,"mummy",score,0.4f,at,at+100)
            if(p.observe(true,r)) confidence.reset()
            return r
        }
        // Four valid attempts, last strong, trigger profile change without confirming.
        frame(0.6f,1000);frame(0.9f,2000);frame(0.6f,3000)
        assertFalse(frame(0.9f,4000).confirmed);assertEquals(FastRegion.UPPER,p.region)
        assertFalse(frame(0.9f,5000).confirmed)
        assertTrue(frame(0.9f,6000).confirmed)
    }
    @Test fun confirmedHybridKeepsArtAndNeverDisablesWholeLane() {
        val p=EdScanFastAutoPolicy()
        p.observe(true,reading("readings"));p.observe(true,reading("confirmed"))
        repeat(8) { p.observe(true,reading("confirmed")) }
        assertEquals(FastRegion.ART,p.region)
    }
    @Test fun firstStrongGetsSecondChanceButUnstableCandidatesEventuallyFallback() {
        val p=EdScanFastAutoPolicy()
        p.observe(true,reading("score"));p.observe(true,reading("readings"))
        assertEquals(FastRegion.ART,p.region)
        p.observe(true,reading("score"));p.observe(true,reading("readings"))
        assertEquals(FastRegion.UPPER,p.region)
    }
    @Test fun staleMissingAndLifecycleResetNeverCountAsVotes() {
        val p=EdScanFastAutoPolicy()
        repeat(5) { p.observe(true,reading("stale")) }
        assertEquals(FastRegion.ART,p.region)
        p.observe(true,reading("score"));p.observe(false,reading("no_detection"))
        p.observe(true,reading("score"));assertEquals(FastRegion.ART,p.region)
        p.observe(true,reading("score"));p.reset();assertEquals(FastRegion.ART,p.region)
    }
}
