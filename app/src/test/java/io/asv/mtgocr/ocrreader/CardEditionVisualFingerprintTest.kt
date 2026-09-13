package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardEditionVisualFingerprintTest {
    @Test fun classifiesBlackWhiteAndGoldBorders() {
        assertEquals(CardBorderColor.BLACK, classify(0xFF171717.toInt()))
        assertEquals(CardBorderColor.BLACK, classify(0xFF4F432D.toInt()))
        assertEquals(CardBorderColor.WHITE, classify(0xFFE8E5D8.toInt()))
        assertEquals(CardBorderColor.WHITE, classify(0xFFD8CDB8.toInt()))
        assertEquals(CardBorderColor.GOLD, classify(0xFFC69A42.toInt()))
    }

    @Test fun matchingSetSymbolAndBorderBreakAnArtworkTie() {
        val matching = CardEditionVisualFingerprint.combinedDistance(
            .12, .08, CardBorderColor.WHITE, CardBorderColor.WHITE)
        val wrongEdition = CardEditionVisualFingerprint.combinedDistance(
            .12, .55, CardBorderColor.WHITE, CardBorderColor.BLACK)
        assertTrue(matching < wrongEdition)
        assertTrue(wrongEdition - matching > .15)
    }

    @Test fun unknownBorderIsNeutralInsteadOfPenalisingCandidate() {
        val unknown = CardEditionVisualFingerprint.combinedDistance(
            .2, .2, CardBorderColor.UNKNOWN, CardBorderColor.BLACK)
        val lowConfidenceMismatch = CardEditionVisualFingerprint.combinedDistance(
            .2, .2, CardBorderColor.WHITE, CardBorderColor.BLACK, 0.0, 1.0)
        assertEquals(unknown, lowConfidenceMismatch, .0001)
    }

    private fun classify(pixel: Int): CardBorderColor =
        CardEditionVisualFingerprint.classifyBorder(IntArray(200) { pixel }).first
}
