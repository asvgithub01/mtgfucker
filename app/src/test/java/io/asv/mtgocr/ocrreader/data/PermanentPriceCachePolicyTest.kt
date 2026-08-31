package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermanentPriceCachePolicyTest {
    @Test
    fun partialRowsCannotShortCircuitAnUnscannedRequest() {
        assertFalse(PermanentPriceCachePolicy.canServeCached(false, false))
    }

    @Test
    fun anExactCompletedScanIsReusableWithoutExpiry() {
        assertTrue(PermanentPriceCachePolicy.canServeCached(false, true))
    }

    @Test
    fun explicitRefreshAlwaysRescans() {
        assertFalse(PermanentPriceCachePolicy.canServeCached(true, true))
    }
}
