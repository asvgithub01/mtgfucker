package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScryfallImageDataProviderTest {
    @Test
    fun parsesOnlyCardsWithCardmarketProductIds() {
        val response = """{
              "data": [
                {"id":"scryfall-1","cardmarket_id":789011},
                {"id":"scryfall-2"},
                {"id":"scryfall-3","cardmarket_id":17812}
              ],
              "not_found": [{"id":"missing"}]
            }"""

        assertEquals(
            mapOf("scryfall-1" to "789011", "scryfall-3" to "17812"),
            ScryfallImageDataProvider.parseCardmarketIdentifiers(response)
        )
    }
}
