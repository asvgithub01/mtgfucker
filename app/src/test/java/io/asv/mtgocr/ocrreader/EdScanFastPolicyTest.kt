package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class EdScanFastPolicyTest {
    @Test fun sameStrictRulesWithoutOcrDelay() {
        val p=EdScanFastPolicy()
        assertFalse(p.observe(true,"a",.90f,.70f,1000,1855).confirmed)
        val r=p.observe(true,"a",.91f,.70f,2005,2860)
        assertTrue(r.confirmed);assertEquals(1860L,r.latencyMs)
    }
    @Test fun sonyCyclopeanMummyScoresAndMarginsRemainRejected() {
        val p=EdScanFastPolicy()
        assertEquals("score",p.observe(true,"mummy-leg",.514f,.512f,1000,1600).reason)
        assertFalse(p.observe(true,"mummy-leg",.514f,.512f,1800,2400).confirmed)
    }
    @Test fun sonyCuombajjPrintingTieIsNotIdentityProof() {
        val p=EdScanFastPolicy()
        repeat(5) { assertEquals("margin",p.observe(true,"witches",.95f,.94f,1000L+it*1000,1800L+it*1000).reason) }
    }
    @Test fun weakDifferentMissingAndOldFramesCannotConfirm() {
        val p=EdScanFastPolicy()
        p.observe(true,"a",.9f,.7f,1000,1800)
        assertFalse(p.observe(true,"b",.9f,.7f,2000,2800).confirmed)
        p.observe(false,null,null,null,3000,3200)
        assertFalse(p.observe(true,"b",.9f,.7f,4000,4800).confirmed)
        assertEquals("stale",p.observe(true,"b",.9f,.7f,5000,7600).reason)
        assertFalse(p.observe(true,"b",.9f,.7f,8000,8800).confirmed)
    }
    @Test fun pauseAndDuplicateSamplesDoNotCount() {
        val p=EdScanFastPolicy()
        p.observe(true,"a",.9f,.7f,1000,1800)
        assertFalse(p.observe(true,"a",.9f,.7f,1000,1900).confirmed)
        p.reset()
        assertFalse(p.observe(true,"a",.9f,.7f,2000,2800).confirmed)
        assertFalse(p.observe(true,"a",.9f,.7f,10000,10800).confirmed)
    }
}
