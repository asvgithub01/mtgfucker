package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardScanStabilityTest {
    @Test fun acceptsStableNameAndSuppressesSamePhysicalCard() {
        val stability = CardScanStability(requiredHits = 2)
        assertFalse(stability.observe("Sol Ring", 100))
        assertTrue(stability.observe("Sol Ring", 500))
        assertFalse(stability.observe("Sol Ring", 900))
        assertFalse(stability.observe("Black Lotus", 1_000))
        assertTrue(stability.observe("Black Lotus", 1_300))
    }

    @Test fun explicitRepeatArmsTheSameNameAgain() {
        val stability = CardScanStability(requiredHits = 2)
        stability.observe("Island", 100)
        assertTrue(stability.observe("Island", 200))
        stability.allowRepeat()
        assertFalse(stability.observe("Island", 300))
        assertTrue(stability.observe("Island", 400))
    }

    @Test fun staleHitsDoNotCombine() {
        val stability = CardScanStability(requiredHits = 2, maximumGapMs = 500)
        assertFalse(stability.observe("Mountain", 100))
        assertFalse(stability.observe("Mountain", 700))
    }

    @Test fun twoClearFramesRearmAnIdenticalPhysicalCopyWithoutDelayingFirstHit() {
        val stability = CardScanStability(requiredHits = 1)
        assertTrue(stability.observe("Island", 100))
        assertFalse(stability.observe("Island", 200))

        stability.observeNoCandidate()
        assertFalse(stability.observe("Island", 300))
        stability.observeNoCandidate()
        stability.observeCandidatePresent()
        stability.observeNoCandidate()
        assertFalse(stability.observe("Island", 350))
        stability.observeNoCandidate()
        stability.observeNoCandidate()
        assertTrue(stability.observe("Island", 400))
    }
}
