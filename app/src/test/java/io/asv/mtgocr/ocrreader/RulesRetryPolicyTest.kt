package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class RulesRetryPolicyTest {
    @Test fun retriesFreshCapturesAtMostThreeTimes() {
        val policy = RulesRetryPolicy()
        assertFalse(policy.shouldRetry("WEAK_ART", true, true))
        for (attempt in 1..3) {
            assertEquals(attempt, policy.beginAttempt())
            assertEquals(attempt < 3, policy.shouldRetry("WEAK_ART", true, true))
        }
        policy.reset()
        assertEquals(1, policy.beginAttempt())
    }
    @Test fun successManualAndStructuralAmbiguityNeverRetry() {
        val policy = RulesRetryPolicy().apply { beginAttempt() }
        for (reason in listOf("CATALOG_UNIQUE_ARTWORK", "STRUCTURED_FOOTER", "DISABLED",
                "RETAINED_FOOTER_POSSIBLE", "UNSUPPORTED_SET_PROFILE", "HISTORICAL_SET"))
            assertFalse(reason, policy.shouldRetry(reason, true, true))
        assertFalse(policy.shouldRetry("WEAK_ART", false, true))
        assertFalse(policy.shouldRetry("WEAK_ART", true, false))
        assertTrue(policy.shouldRetry("FOOTER_NEEDS_CORROBORATION", true, true))
    }
    @Test fun savedZoomIsClampedAndLinearEndpointsAreCorrect() {
        assertEquals(2f, ScannerZoomPolicy.clamp(2f, 1f, 8f), .001f)
        assertEquals(4f, ScannerZoomPolicy.clamp(8f, 1f, 4f), .001f)
        assertEquals(1f, ScannerZoomPolicy.clamp(Float.NaN, 1f, 8f), .001f)
        assertEquals(1f, ScannerZoomPolicy.fromLinear(0f, 1f, 8f), .001f)
        assertEquals(8f, ScannerZoomPolicy.fromLinear(1f, 1f, 8f), .001f)
        assertEquals(1f / .5625f, ScannerZoomPolicy.fromLinear(.5f, 1f, 8f), .001f)
    }
}
