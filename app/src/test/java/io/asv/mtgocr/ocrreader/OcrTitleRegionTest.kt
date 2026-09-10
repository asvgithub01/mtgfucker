package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTitleRegionTest {
    @Test fun titleBandOnlyAcceptsTheTopOfTheCenteredCard() {
        val region = OcrTitleRegion.forFrame(1080, 2400)
        assertTrue(region.contains((region.left + region.right) / 2, (region.top + region.bottom) / 2))
        assertFalse(region.contains(540, 1200))
        assertFalse(region.contains(10, 10))
    }
}
