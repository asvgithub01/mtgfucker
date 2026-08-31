package io.asv.mtgocr.ocrreader.data

import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LegacyCollectionStoreTest {
    @Test
    fun addCopy_incrementsTheSamePrintingAndNormalizesLegacyNonfoil() {
        val collection = Biblio("myBiblio.Json", "Test")
        val owned = CardInfo("Mox Opal", "", "", "", "1").apply {
            printingUuid = "printing-1"
            setCode = "MM2"
            finish = "normal"
        }
        collection.addCard(owned)

        val result = LegacyCollectionStore.addCopyToCollection(collection, option(finish = "nonfoil"))

        assertSame(owned, result)
        assertEquals(2, owned.quantityCount)
        assertEquals(1, collection.cards.size)
    }

    @Test
    fun addCopy_keepsFoilAsADifferentPhysicalCard() {
        val collection = Biblio("myBiblio.Json", "Test")
        collection.addCard(CardInfo("Mox Opal", "", "", "", "1").apply {
            printingUuid = "printing-1"
            finish = "nonfoil"
        })

        val foil = LegacyCollectionStore.addCopyToCollection(collection, option(finish = "foil"))

        assertEquals(2, collection.cards.size)
        assertEquals("foil", foil.finish)
        assertEquals(1, foil.quantityCount)
    }

    @Test
    fun addCopy_matchesPrintingUuidEvenWhenStoredNameIsLocalized() {
        val collection = Biblio("myBiblio.Json", "Test")
        val localized = CardInfo("Ópalo de mox", "", "", "", "1").apply {
            printingUuid = "printing-1"
            finish = "nonfoil"
        }
        collection.addCard(localized)

        val result = LegacyCollectionStore.addCopyToCollection(collection, option(finish = "nonfoil"))

        assertSame(localized, result)
        assertEquals(2, localized.quantityCount)
        assertEquals(1, collection.cards.size)
    }

    @Test
    fun addCopy_doesNotMergeNearMintWithAPlayedCopy() {
        val collection = Biblio("myBiblio.Json", "Test")
        val played = CardInfo("Mox Opal", "", "", "", "1").apply {
            printingUuid = "printing-1"
            finish = "nonfoil"
            condition = "played"
        }
        collection.addCard(played)

        val nearMint = LegacyCollectionStore.addCopyToCollection(collection, option(finish = "nonfoil"))

        assertEquals(2, collection.cards.size)
        assertEquals("played", played.condition)
        assertEquals("near_mint", nearMint.condition)
    }

    private fun option(finish: String) = CardEditionOption(
        printingUuid = "printing-1",
        cardName = "Mox Opal",
        displayName = "Mox Opal",
        setCode = "MM2",
        setName = "Modern Masters 2015",
        collectorNumber = "223",
        releaseDate = "2015-05-22",
        rarity = "mythic",
        finish = finish,
        isFoil = finish == "foil",
        imageUrl = "https://example.invalid/card.jpg",
        typeLine = "Legendary Artifact",
        rulesText = "Metalcraft",
        price = 42.0,
        currency = "EUR",
        priceProvider = "test",
        priceDate = "2026-08-24"
    )
}
