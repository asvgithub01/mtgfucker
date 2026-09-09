package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceMathTest {
    @Test
    fun parsesBothDecimalSeparatorsAndThousands() {
        assertEquals(12.34, PriceMath.parse("12,34 €")!!, 0.0001)
        assertEquals(1234.56, PriceMath.parse("1.234,56 EUR")!!, 0.0001)
        assertEquals(1234.56, PriceMath.parse("$1,234.56 USD")!!, 0.0001)
    }

    @Test
    fun convertsEurAndUsdInBothDirections() {
        assertEquals(20.0, PriceMath.convert(10.0, "EUR", "USD", 2.0), 0.0001)
        assertEquals(10.0, PriceMath.convert(20.0, "USD", "EUR", 2.0), 0.0001)
    }
}
