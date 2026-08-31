package io.asv.mtgocr.ocrreader.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CardConditionTest {
    @Test fun defaultsUnknownAndLegacyCardsToNearMint() {
        assertEquals(CardCondition.NEAR_MINT, CardCondition.normalize(null))
        assertEquals(1.0, CardCondition.multiplier("unexpected"), 0.0)
    }

    @Test fun adjustsTheNearMintPriceAndPreservesCurrency() {
        assertEquals("9.00 EUR", CardCondition.adjustedDisplay("10.00 EUR", "10.0", "excellent"))
        assertEquals("10.00 EUR", CardCondition.adjustedDisplay("10.00 EUR", "10.0", "near_mint"))
    }

    @Test fun lowerConditionsProduceLowerEstimates() {
        assertEquals(7.0, CardCondition.adjustedAmount(10.0, "light_played"), 0.0001)
        assertEquals(3.5, CardCondition.adjustedAmount(10.0, "poor"), 0.0001)
    }
}
