package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class CardImageFingerprintTest {
    @Test fun normalizedDistanceCountsChangedBits() {
        val empty = longArrayOf(0L, 0L, 0L, 0L)
        val half = longArrayOf(-1L, -1L, 0L, 0L)
        assertEquals(0.5, CardImageFingerprint.normalizedDistance(empty, half), 0.0001)
        assertEquals(0.0, CardImageFingerprint.normalizedDistance(empty, empty), 0.0001)
    }

    @Test fun mismatchedFingerprintsAreRejected() {
        assertEquals(
            1.0,
            CardImageFingerprint.normalizedDistance(longArrayOf(0L), longArrayOf(0L, 0L)),
            0.0001
        )
    }
}
