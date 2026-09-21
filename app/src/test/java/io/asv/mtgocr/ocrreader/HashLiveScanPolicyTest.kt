package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HashLiveScanPolicyTest {
    @Test
    fun waitsForSeveralCoherentDetections() {
        assertFalse(HashLiveScanPolicy.shouldAnalyze(.8f, 2))
        assertFalse(HashLiveScanPolicy.shouldAnalyze(.33f, 5))
        assertTrue(HashLiveScanPolicy.shouldAnalyze(.34f, 3))
    }
}
