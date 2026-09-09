package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSessionCountsTest {
    @Test
    fun rowsSelectionsAndOrdinalsUsePhysicalCopies() {
        val first = card("A", 2)
        val second = card("B", 1)
        val third = card("C", 3)
        val cards = listOf(first, second, third)

        assertEquals(6, ScanSessionCounts.total(cards))
        assertEquals(5, ScanSessionCounts.selected(cards, setOf(first.collectionItemId, third.collectionItemId)))
        assertEquals(1..2, ScanSessionCounts.range(cards, 0))
        assertEquals(4..6, ScanSessionCounts.range(cards, 2))
    }

    @Test
    fun priceProgressCountsOnlyPhysicalCopiesIncludedInTheTotal() {
        val medianPrice = card("A", 2).apply { priceM = "0.20" }
        val basePrice = CardInfo("B", "1,50 EUR", "", "", "2")
        val unavailable = CardInfo("C", "sin precio", "", "", "3")
        val cards = listOf(medianPrice, basePrice, unavailable)

        assertEquals(7, ScanSessionCounts.total(cards))
        assertEquals(4, ScanSessionCounts.priced(cards))
    }

    private fun card(name: String, quantity: Int) = CardInfo(name, "", "", "", quantity.toString())
}
