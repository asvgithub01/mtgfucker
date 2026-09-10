package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionSortTest {
    private val alpha = card("Alpha")
    private val bravo = card("bravo")
    private val charlie = card("Charlie")
    private val chronological = listOf(bravo, charlie, alpha)

    @Test
    fun `entry order keeps the existing newest-first presentation`() {
        assertEquals(
            listOf(alpha, charlie, bravo),
            ScanSessionSort.sorted(chronological, ScanSessionSort.ENTRY)
        )
    }

    @Test
    fun `alphabetical and price orders do not mutate chronological ordinals`() {
        assertEquals(
            listOf(alpha, bravo, charlie),
            ScanSessionSort.sorted(chronological, ScanSessionSort.ALPHABETICAL)
        )
        assertEquals(
            listOf(charlie, bravo, alpha),
            ScanSessionSort.sorted(
                chronological,
                ScanSessionSort.PRICE_DESCENDING,
                priceOf = { mapOf(alpha to null, bravo to 2.0, charlie to 8.0)[it] }
            )
        )
        assertEquals(listOf(bravo, charlie, alpha), chronological)
        assertEquals(1..1, ScanSessionCounts.range(chronological, 0))
    }

    @Test
    fun `incomplete cards come first and missing prices sort last`() {
        assertEquals(
            listOf(charlie, alpha, bravo),
            ScanSessionSort.sorted(
                chronological,
                ScanSessionSort.INCOMPLETE_FIRST,
                incomplete = { it === charlie }
            )
        )
        assertFalse(ScanSessionSort.isComplete(alpha))
        assertTrue(ScanSessionSort.isComplete(completeCard("Ready")))
    }

    private fun card(name: String) = CardInfo(name, "", "", "", "1")

    private fun completeCard(name: String) = card(name).apply {
        priceM = "3.50"
        imgPath = "https://example.test/card.jpg"
        printingUuid = "printing-id"
        setCode = "TST"
        collectorNumber = "7"
        description = "Rules text"
    }
}
