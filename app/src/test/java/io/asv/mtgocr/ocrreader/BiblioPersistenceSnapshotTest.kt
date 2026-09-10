package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import io.asv.mtgocr.ocrreader.model.DescriptionMtgInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.io.ObjectStreamClass

class BiblioPersistenceSnapshotTest {
    @Test fun serializationIdRemainsCompatibleWithTheOriginalCollection() {
        assertEquals(
            -2428653643628406405L,
            ObjectStreamClass.lookup(Biblio::class.java).serialVersionUID
        )
    }

    @Test fun snapshotDetachesCardsAndEveryMutableNestedList() {
        val collection = Biblio("myBiblio.Json", "Biblioteca")
        val card = CardInfo("Exile", "1.50 USD", "rules", "image", "1")
        card.setCode = "ALL"
        card.personalCollections.add("Antiguas")
        card.decks.add("Blanco")
        val translated = DescriptionMtgInfo().apply {
            name = "Exilio"
            description = "texto"
            languague = "es"
        }
        card.lstDescription.add(translated)
        collection.addCard(card)

        val snapshot = collection.snapshotForPersistence()
        val savedCard = snapshot.cards.single()

        assertNotSame(collection, snapshot)
        assertNotSame(card, savedCard)
        assertEquals(card.collectionItemId, savedCard.collectionItemId)
        assertEquals("ALL", savedCard.setCode)

        card.personalCollections.add("Mutada")
        card.decks.clear()
        translated.name = "Cambiado"
        collection.cards.clear()

        assertEquals(listOf("Antiguas"), savedCard.personalCollections)
        assertEquals(listOf("Blanco"), savedCard.decks)
        assertEquals("Exilio", savedCard.lstDescription.single().name)
        assertEquals(1, snapshot.cards.size)
    }
}
