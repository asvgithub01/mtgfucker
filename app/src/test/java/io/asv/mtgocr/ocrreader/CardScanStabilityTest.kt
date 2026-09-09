package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardScanStabilityTest {
    @Test fun acceptsStableNameAndSuppressesSamePhysicalCard() {
        val stability = CardScanStability(requiredHits = 2, minimumStableMs = 300)
        assertFalse(stability.observe("Sol Ring", 100))
        assertTrue(stability.observe("Sol Ring", 400))
        assertFalse(stability.observe("Sol Ring", 900))
        assertFalse(stability.observe("Black Lotus", 1_000))
        assertTrue(stability.observe("Black Lotus", 1_300))
    }

    @Test fun explicitRepeatArmsTheSameNameAgain() {
        val stability = CardScanStability(requiredHits = 2, minimumStableMs = 100)
        stability.observe("Island", 100)
        assertTrue(stability.observe("Island", 200))
        stability.allowRepeat()
        assertFalse(stability.observe("Island", 300))
        assertTrue(stability.observe("Island", 400))
    }

    @Test fun staleHitsDoNotCombine() {
        val stability = CardScanStability(
            requiredHits = 2,
            maximumGapMs = 500,
            minimumStableMs = 100
        )
        assertFalse(stability.observe("Mountain", 100))
        assertFalse(stability.observe("Mountain", 700))
    }

    @Test fun partialNameMustNotWinBeforeFullNameStabilizes() {
        val stability = CardScanStability(requiredHits = 2, minimumStableMs = 300)
        assertFalse(stability.observe("Ira", 0))
        assertFalse(stability.observe("Hija de la Ira", 100))
        assertFalse(stability.observe("Hija de la Ira", 300))
        assertTrue(stability.observe("Hija de la Ira", 400))
    }

    @Test fun oneEmptyFrameDoesNotRearmSameVisibleCard() {
        val stability = CardScanStability(
            requiredHits = 2,
            minimumStableMs = 100,
            requiredClearHits = 2,
            minimumClearMs = 250
        )
        stability.observe("Island", 0)
        assertTrue(stability.observe("Island", 100))

        stability.observeNoCandidate(200)
        assertFalse(stability.observe("Island", 500))
    }

    @Test fun sustainedEmptyTransitionRearmsConsecutiveIdenticalCopy() {
        val stability = CardScanStability(
            requiredHits = 2,
            minimumStableMs = 100,
            requiredClearHits = 2,
            minimumClearMs = 250
        )
        stability.observe("Island", 0)
        assertTrue(stability.observe("Island", 100))

        stability.observeNoCandidate(200)
        stability.observeNoCandidate(450)
        assertFalse(stability.observe("Island", 600))
        assertTrue(stability.observe("Island", 700))
    }

    @Test fun transientDifferentMatchDoesNotRearmAcceptedCard() {
        val stability = CardScanStability(requiredHits = 2, minimumStableMs = 100)
        stability.observe("Sol Ring", 0)
        assertTrue(stability.observe("Sol Ring", 100))

        assertFalse(stability.observe("Soul Ring", 200))
        assertFalse(stability.observe("Sol Ring", 300))
    }
}
