package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolResolutionPolicyTest {
    @Test fun v2KeepsTwiceTheLinearCardResolution() {
        assertEquals(1260, SymbolResolutionPolicy.cardWidth(true))
        assertEquals(1760, SymbolResolutionPolicy.cardHeight(true))
        assertEquals(1488, SymbolResolutionPolicy.segmentationWidth(1260))
    }
    @Test fun legacyAndOldPhotosDoNotPretendToHaveMorePixels() {
        assertEquals(630, SymbolResolutionPolicy.cardWidth(false))
        assertEquals(880, SymbolResolutionPolicy.cardHeight(false))
        assertEquals(744, SymbolResolutionPolicy.segmentationWidth(630))
    }
}
