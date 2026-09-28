package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test

class ScanPartyOptionsTest {
    @Test fun extrasAreOptInAndKeepOriginalDurationByDefault() {
        val options = ScanPartyOptions()
        assertFalse(options.balloons)
        assertFalse(options.trumpets)
        assertFalse(options.disco)
        assertEquals(1800L, options.durationMillis)
    }

    @Test fun eachEffectAndEveryCombinationLastFourSeconds() {
        for (bits in 1..7) {
            val options = ScanPartyOptions(bits and 1 != 0, bits and 2 != 0, bits and 4 != 0)
            assertEquals(4000L, options.durationMillis)
            assertEquals(bits and 1 != 0, options.balloons)
            assertEquals(bits and 2 != 0, options.trumpets)
            assertEquals(bits and 4 != 0, options.disco)
        }
    }
}
