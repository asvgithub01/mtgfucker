package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class RulesCaptureGateTest {
    private fun q(t: Double, f: Double) = mapOf("titleLaplacianVariance" to t, "footerLaplacianVariance" to f)
    @Test fun retriesAreBounded() {
        val gate = RulesCaptureGate()
        assertTrue(gate.shouldRetry(q(3.26, 1853.0)))
        assertTrue(gate.shouldRetry(q(3.43, 1161.0)))
        repeat(4) { assertFalse(gate.shouldRetry(q(3.0, 1000.0))) }
    }
    @Test fun normalCardResetsBudget() {
        val gate = RulesCaptureGate()
        repeat(2) { assertTrue(gate.shouldRetry(q(3.0, 1000.0))) }
        assertFalse(gate.shouldRetry(q(300.0, 1000.0)))
        assertTrue(gate.shouldRetry(q(3.0, 1000.0)))
    }
    @Test fun unknownBlankAndBoundariesNotRejected() {
        for (quality in listOf(emptyMap(), q(Double.NaN, 400.0), q(-1.0, 400.0),
            q(3.0, Double.POSITIVE_INFINITY), q(0.0, 0.0), q(10.0, 400.0), q(3.0, 100.0)))
            assertFalse(RulesCaptureGate.suspicious(quality))
    }
}
