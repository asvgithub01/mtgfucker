package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFinishTest {
    @Test fun recognizesFoilAndEtchedFinishes() {
        assertTrue(CardFinish.isFoil("foil"))
        assertTrue(CardFinish.isFoil(" Etched "))
        assertTrue(CardFinish.isFoil("etched-foil"))
    }

    @Test fun regularAndMissingFinishesAreNotFoil() {
        assertFalse(CardFinish.isFoil("nonfoil"))
        assertFalse(CardFinish.isFoil("normal"))
        assertFalse(CardFinish.isFoil(null))
    }
}
