package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleGalleryPopupLayoutTest {
    @Test fun phoneReservesCardWidthBeforeMenu() {
        val s=TripleGalleryPopupLayout.fit(384,700,320,128,8)
        assertEquals(320,s.height);assertEquals(229,s.cardWidth);assertEquals(147,s.menuWidth)
    }
    @Test fun smallScreenKeepsAspectAndScrollableControls() {
        val s=TripleGalleryPopupLayout.fit(304,500,360,128,8)
        assertTrue(s.menuWidth>=128);assertTrue(s.height<360)
        assertEquals(s.height*63f/88f,s.cardWidth.toFloat(),1f)
        assertEquals(304,s.cardWidth+s.menuWidth+8)
    }
    @Test fun landscapeNeverExceedsVisibleHeight() {
        val s=TripleGalleryPopupLayout.fit(760,260,340,128,8)
        assertEquals(260,s.height);assertEquals(760,s.cardWidth+s.menuWidth+8)
    }
}
