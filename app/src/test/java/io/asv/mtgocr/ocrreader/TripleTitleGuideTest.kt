package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleTitleGuideTest {
    @Test fun guideMatchesOcrRegionInPortraitLandscapeAndSmallPreview() {
        listOf(1080 to 1500,1500 to 600,320 to 240).forEach { (w,h) ->
            val b=OcrTitleRegion.forFrame(w,h)
            val points=TripleTitleGuide.points(w,h)
            assertArrayEquals(floatArrayOf(b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat(),b.left.toFloat(),b.bottom.toFloat()),points,0f)
            assertTrue(b.left>=0 && b.right<=w && b.top>=0 && b.bottom<=h)
            assertTrue(b.right>b.left && b.bottom>b.top)
        }
    }
}
