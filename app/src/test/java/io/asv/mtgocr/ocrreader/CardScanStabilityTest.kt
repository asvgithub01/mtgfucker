package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class CardScanStabilityTest {
    @Test fun acceptsStableNameAndOffersSameCardAgainAfterCooldown() {
        val stability = CardScanStability(requiredHits = 2)
        assertEquals(waiting, stability.observe("Sol Ring", "Sol Ring", "en", 100))
        assertEquals(accept, stability.observe("Sol Ring", "Sol Ring", "en", 500))
        assertEquals(waiting, stability.observe("Sol Ring", "Sol Ring", "en", 900))
        assertEquals(confirm, stability.observe("Sol Ring", "Sol Ring", "en", 3_000))
    }

    @Test fun explicitRepeatArmsTheSameNameAgain() {
        val stability = CardScanStability(requiredHits = 2)
        stability.observe("Island", "Island", "en", 100)
        assertEquals(accept, stability.observe("Island", "Island", "en", 200))
        stability.allowRepeat()
        assertEquals(waiting, stability.observe("Island", "Island", "en", 300))
        assertEquals(accept, stability.observe("Island", "Island", "en", 400))
    }

    @Test fun staleHitsDoNotCombine() {
        val stability = CardScanStability(requiredHits = 2, maximumGapMs = 500)
        assertEquals(waiting, stability.observe("Mountain", "Mountain", "en", 100))
        assertEquals(waiting, stability.observe("Mountain", "Mountain", "en", 700))
    }

    @Test fun differentLocalizedLanguageIsAcceptedWithoutCooldownOrPrompt() {
        val stability = CardScanStability(requiredHits = 1)
        assertEquals(accept, stability.observe("Fog", "Fog", "en", 100))
        assertEquals(accept, stability.observe("Fog", "Niebla", "es", 200))
    }

    @Test fun rejectingRepeatRestartsCooldown() {
        val stability = CardScanStability(requiredHits = 1, repeatCooldownMs = 2_500)
        assertEquals(accept, stability.observe("Fog", "Niebla", "es", 100))
        assertEquals(confirm, stability.observe("Fog", "Niebla", "es", 2_600))
        stability.postponeRepeat(3_000)
        assertEquals(waiting, stability.observe("Fog", "Niebla", "es", 5_000))
        assertEquals(confirm, stability.observe("Fog", "Niebla", "es", 5_500))
    }

    private companion object {
        val waiting = CardScanStability.Decision.WAITING
        val accept = CardScanStability.Decision.ACCEPT
        val confirm = CardScanStability.Decision.CONFIRM_REPEAT
    }
}
