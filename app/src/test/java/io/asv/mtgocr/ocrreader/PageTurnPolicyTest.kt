package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PageTurnPolicyTest {
    @Test fun commitsAfterAboutAQuarterOfThePageWidth() {
        val fraction = PageTurnPolicy.dragFraction(-250f, 1_000)
        assertTrue(abs(fraction) >= PageTurnPolicy.COMMIT_FRACTION)
        assertTrue(PageTurnPolicy.shouldCommit(fraction, targetReady = true))
    }

    @Test fun doesNotCommitAnIncompleteOrUnavailableTurn() {
        val shortDrag = PageTurnPolicy.dragFraction(150f, 1_000)
        assertFalse(PageTurnPolicy.shouldCommit(shortDrag, targetReady = true))
        assertFalse(PageTurnPolicy.shouldCommit(1f, targetReady = false))
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
