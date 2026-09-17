package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class SetSymbolShapeMatcherTest {
    @Test fun keepsHashDistanceWhenShapeResultIsNotReliable() {
        assertEquals(.32, SetSymbolShapeMatcher.fuseDistance(.32, .05, false), .0001)
    }

    @Test fun reliableShapeEvidenceDominatesTheLegacyHash() {
        val distance = SetSymbolShapeMatcher.fuseDistance(.70, .10, true)

        assertEquals(.232, distance, .0001)
    }
}
