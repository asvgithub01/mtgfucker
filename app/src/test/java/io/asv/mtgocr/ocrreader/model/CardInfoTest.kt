package io.asv.mtgocr.ocrreader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class CardInfoTest {
    @Test
    fun organizationAndPrintingMetadataSurviveSerialization() {
        val card = CardInfo("Mox Opal", "42.00 EUR", "", "image", "1").apply {
            printingUuid = "printing-1"
            setCode = "MM2"
            setName = "Modern Masters 2015"
            collectorNumber = "223"
            finish = "foil"
            addGroup("Favoritas")
            setDeckZone("Affinity", true)
        }

        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(card) }
            output.toByteArray()
        }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as CardInfo
        }

        assertEquals("printing-1", restored.printingUuid)
        assertEquals("MM2", restored.setCode)
        assertEquals("Modern Masters 2015", restored.setName)
        assertEquals("223", restored.collectorNumber)
        assertEquals("foil", restored.finish)
        assertEquals(listOf("Favoritas"), restored.groups)
        assertEquals(listOf("Affinity"), restored.decks)
        assertTrue(restored.isSideboardForDeck("Affinity"))
        assertTrue(restored.addedAt > 0L)
    }

    @Test
    fun deckZoneCanMoveBetweenMainAndSideboardAndIsRemovedAtomically() {
        val card = CardInfo("Mox Opal", "", "", "", "4")

        assertTrue(card.setDeckZone("Affinity", false))
        assertFalse(card.isSideboardForDeck("Affinity"))
        assertTrue(card.setDeckZone("Affinity", true))
        assertTrue(card.isSideboardForDeck("Affinity"))
        assertTrue(card.removeDeck("Affinity"))
        assertFalse(card.decks.contains("Affinity"))
        assertFalse(card.sideboardDecks.contains("Affinity"))
    }

    @Test
    fun groupMembershipDoesNotCreateDuplicates() {
        val card = CardInfo("Mox Opal", "", "", "", "")

        assertTrue(card.addDeck("Affinity"))
        assertFalse(card.addDeck("Affinity"))
        assertTrue(card.addGroup("Cambio"))
        assertFalse(card.addGroup("Cambio"))
        assertEquals(1, card.decks.size)
        assertEquals(1, card.groups.size)
    }

    @Test
    fun quantityDefaultsToOneAndNeverDropsBelowOne() {
        val card = CardInfo("Mox Opal", "", "", "", "")

        assertEquals(1, card.quantityCount)
        assertTrue(card.ensureQuantity())
        assertEquals("1", card.quantity)
        card.quantityCount = 4
        assertEquals(4, card.quantityCount)
        card.quantityCount = 0
        assertEquals(1, card.quantityCount)
    }
}
