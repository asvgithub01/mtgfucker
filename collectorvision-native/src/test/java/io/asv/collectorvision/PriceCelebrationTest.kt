package io.asv.collectorvision
import org.junit.Test
import org.junit.Assert.*
class PriceCelebrationTest {
    @Test fun sliderIncludesEndpointsAndDefault() {
        assertEquals(.5, PriceCelebration.thresholdForProgress(0), .0001)
        assertEquals(25.0, PriceCelebration.thresholdForProgress(49), .0001)
        assertEquals(10.0, PriceCelebration.thresholdForProgress(19), .0001)
        assertEquals(19, PriceCelebration.progressForThreshold(Double.NaN))
        assertEquals(.5, PriceCelebration.thresholdForProgress(-1), .0001)
        assertEquals(25.0, PriceCelebration.thresholdForProgress(99), .0001)
        assertTrue(PriceCelebration.eligible(25.0, 25.0))
        assertFalse(PriceCelebration.eligible(24.99, 25.0))
        assertFalse(PriceCelebration.eligible(30.0, 25.01))
        (0..49).forEach { assertEquals(it, PriceCelebration.progressForThreshold(PriceCelebration.thresholdForProgress(it))) }
    }
    @Test fun customThresholdIncludesBoundaryAndRejectsInvalidValues() {
        assertTrue(PriceCelebration.eligible(.50, .50))
        assertTrue(PriceCelebration.eligible(.51, .50))
        assertTrue(PriceCelebration.eligible(20.0, 20.0))
        assertTrue(PriceCelebration.eligible(20.01, 20.0))
        assertFalse(PriceCelebration.eligible(Double.NaN, .5))
        assertFalse(PriceCelebration.eligible(30.0, Double.NaN))
    }
    @Test fun thresholdStartsAtTenEuros() {
        assertTrue(PriceCelebration.eligible(10.0))
        assertFalse(PriceCelebration.eligible(9.99))
        assertFalse(PriceCelebration.eligible(Double.NaN))
        assertFalse(PriceCelebration.eligible(Double.POSITIVE_INFINITY))
        assertTrue(PriceCelebration.eligible(10.01))
    }
}
