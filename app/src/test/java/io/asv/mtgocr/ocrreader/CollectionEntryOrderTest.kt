package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionEntryOrderTest {
    private fun card(name: String, time: Long) =
        CardInfo(name, "", "", "", "1").apply { addedAt = time }

    @Test fun `latest scanned cards precede older cards without changing stored chronology`() {
        val old = card("Old", 100)
        val recent = card("Recent", 300)
        val middle = card("Middle", 200)
        val stored = listOf(old, recent, middle)
        assertEquals(listOf(recent, middle, old), stored.sortedWith(CollectionEntryOrder.NEWEST_FIRST))
        assertEquals(listOf(old, recent, middle), stored)
        assertEquals(listOf(100L, 300L, 200L), stored.map { it.addedAt })
    }

    @Test fun `equal timestamps keep stable order and unknown dates remain last`() {
        val unknown = card("Legacy", 0)
        val first = card("First", 100)
        val second = card("Second", 100)
        assertEquals(listOf(first, second, unknown),
            listOf(unknown, first, second).sortedWith(CollectionEntryOrder.NEWEST_FIRST))
    }
}
