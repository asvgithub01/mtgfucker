package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test

class ScanPartyOptionsTest {
    @Test fun exactDoublingThresholdsAreInclusiveAndCumulative() {
        for (base in listOf(.5, 1.0, 3.5, 20.0, 25.0)) {
            for (level in 1..5) {
                val price = base * (1 shl (level - 1))
                val options = ScanPartyOptions.forPrice(price, base)!!
                assertEquals(level, options.level)
                assertEquals(level >= 2, options.balloons)
                assertEquals(level >= 3, options.trumpets)
                assertEquals(level >= 4, options.disco)
                assertEquals(level >= 5, options.dancer)
                assertEquals(if (level == 5) 5000L else 4000L, options.durationMillis)
                assertEquals(if (level == 1) null else level - 1,
                    ScanPartyOptions.forPrice(price - .001, base)?.level)
            }
        }
    }
    @Test fun invalidOrBelowThresholdPricesNeverCelebrate() {
        for (price in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0, .99)) {
            assertNull(ScanPartyOptions.forPrice(price, 1.0))
        }
        for (base in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 25.01)) {
            assertNull(ScanPartyOptions.forPrice(100.0, base))
        }
    }
    @Test fun pricesAboveTopTierStayAtDancer() {
        assertEquals(5, ScanPartyOptions.forPrice(10000.0, .5)!!.level)
    }
}
