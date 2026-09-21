package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CardAspectPolicyTest {
    @Test fun acceptsCardRatioWithModeratePerspective() {
        assertNotNull(CardAspectPolicy.measure(top = 620.0, right = 950.0, bottom = 700.0, left = 900.0))
    }

    @Test fun rejectsTallCropWhoseTopIsFarAboveTheCard() {
        assertNull(CardAspectPolicy.measure(top = 550.0, right = 920.0, bottom = 550.0, left = 920.0))
    }

    @Test fun rejectsUnbalancedOppositeEdges() {
        assertNull(CardAspectPolicy.measure(top = 430.0, right = 900.0, bottom = 700.0, left = 900.0))
    }
}
