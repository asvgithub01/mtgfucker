package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test

class FastLaneChoiceTest {
    @Test fun conflictingStrongIdentitiesRejectInsteadOfComparingScores() {
        assertTrue(FastLaneChoice.choose(true,true,false).conflict)
    }
    @Test fun fullArtStrongRetainsWholeCardEvidence() {
        assertEquals(FastLaneChoice.Choice(true,false),FastLaneChoice.choose(true,false,false))
    }
    @Test fun cropStrongCanWinWhenWholeCardIsWeak() {
        assertEquals(FastLaneChoice.Choice(false,false),FastLaneChoice.choose(false,true,false))
    }
    @Test fun agreementUsesFullAndWeakIsNotPromoted() {
        assertEquals(FastLaneChoice.Choice(true,false),FastLaneChoice.choose(true,true,true))
        assertEquals(FastLaneChoice.Choice(false,false),FastLaneChoice.choose(false,false,false))
    }
    @Test fun weakSeparatedFullIsOnlyAPreviewAndCannotOverrideStrongCrop() {
        assertEquals(FastLaneChoice.Choice(true,false),FastLaneChoice.choose(false,false,false,true))
        assertEquals(FastLaneChoice.Choice(false,false),FastLaneChoice.choose(false,true,false,true))
        assertTrue(FastLaneChoice.choose(true,true,false,true).conflict)
    }
}
