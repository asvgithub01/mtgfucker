package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSetLockPolicyTest {
    @Test fun secretsOfStrixhavenLockIncludesItsMysticalArchive() {
        assertEquals(setOf("SOS", "SOA"), ScanSetLockPolicy.expand(setOf("sos")))
    }

    @Test fun unrelatedSetLocksRemainExact() {
        assertEquals(setOf("FDN"), ScanSetLockPolicy.expand(setOf(" FDN ")))
    }
}
