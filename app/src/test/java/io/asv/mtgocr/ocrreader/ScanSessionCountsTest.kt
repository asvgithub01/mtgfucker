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
    fun groupFilterSeedsTheSessionWithEveryExistingGroupCard() {
        val first = card("A", 2).apply { addGroup("Caja antigua") }
        val second = card("B", 1)
        val third = card("C", 3).apply { addGroup("Caja antigua") }

        val session = ScanSessionCounts.cardsInGroup(
            listOf(first, second, third),
            "Caja antigua"
        )

        assertEquals(listOf(first, third), session)
        assertEquals(5, ScanSessionCounts.total(session))
        assertEquals(emptyList<CardInfo>(), ScanSessionCounts.cardsInGroup(listOf(first), ""))
    }

    private fun card(name: String, quantity: Int) = CardInfo(name, "", "", "", quantity.toString())
}
