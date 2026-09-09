package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PageTurnPolicyTest {
    @Test fun commitsAfterAboutFifteenPercentOfThePageWidth() {
        val fraction = PageTurnPolicy.dragFraction(-155f, 1_000)
        assertTrue(abs(fraction) >= PageTurnPolicy.COMMIT_FRACTION)
        assertTrue(PageTurnPolicy.shouldCommit(fraction, targetReady = true))
    }

    @Test fun doesNotCommitAnIncompleteOrUnavailableTurn() {
        val shortDrag = PageTurnPolicy.dragFraction(120f, 1_000)
        assertFalse(PageTurnPolicy.shouldCommit(shortDrag, targetReady = true))
        assertFalse(PageTurnPolicy.shouldCommit(1f, targetReady = false))
    }

    @Test fun commitsAShortIntentionalFlingInTheDragDirection() {
        val shortDrag = PageTurnPolicy.dragFraction(-80f, 1_000)

        assertTrue(PageTurnPolicy.shouldCommit(shortDrag, velocityX = -0.8f, targetReady = true))
        assertFalse(PageTurnPolicy.shouldCommit(shortDrag, velocityX = 0.8f, targetReady = true))
    }

    @Test fun deliberateMovementBackwardsCancelsTheActiveTurn() {
        assertFalse(
            PageTurnPolicy.shouldCancelForReversal(
                initialDirection = -1,
                furthestDistance = 220f,
                currentDistance = -215f,
                tolerance = 12f
            )
        )
        assertTrue(
            PageTurnPolicy.shouldCancelForReversal(
                initialDirection = -1,
                furthestDistance = 220f,
                currentDistance = -190f,
                tolerance = 12f
            )
        )
    }
}
