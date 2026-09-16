package io.asv.mtgocr.ocrreader.data

import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun enrichCardmarketIdentifiers_updatesOnlyTheMatchingPhysicalPrinting() {
        val collection = Biblio("myBiblio.Json", "Test")
        val matching = CardInfo("Mox Opal", "42 EUR", "", "image", "1").apply {
            printingUuid = "printing-1"
            setCode = "MM2"
        }
        val other = CardInfo("Lightning Bolt", "1 EUR", "", "other-image", "1").apply {
            printingUuid = "printing-2"
        }
        collection.addCard(matching)
        collection.addCard(other)

        val changed = LegacyCollectionStore.enrichCardmarketIdentifiers(
            collection,
            mapOf(
                "printing-1" to LegacyCollectionStore.CardmarketPrintingMetadata(
                    mcmId = "282253",
                    mcmSetId = 1645,
                    mcmSetName = "Modern Masters 2015"
                )
            )
        )

        assertEquals(1, changed)
        assertEquals("282253", matching.mcmId)
        assertEquals(1645, matching.mcmSetId)
        assertEquals("Modern Masters 2015", matching.mcmSetName)
        assertEquals("42 EUR", matching.price)
        assertEquals("image", matching.imgPath)
        assertEquals("", other.mcmId)
        assertNull(other.mcmSetId)
    }

    @Test
    fun enrichCardmarketIdentifiers_doesNotReplaceAnExistingExactId() {
        val collection = Biblio("myBiblio.Json", "Test")
        val card = CardInfo("Mox Opal", "", "", "", "1").apply {
            printingUuid = "printing-1"
            mcmId = "already-exact"
        }
        collection.addCard(card)

        LegacyCollectionStore.enrichCardmarketIdentifiers(
            collection,
            mapOf(
                "printing-1" to LegacyCollectionStore.CardmarketPrintingMetadata(
                    mcmId = "new-value",
                    mcmSetId = 1645
                )
            )
        )

        assertEquals("already-exact", card.mcmId)
        assertEquals(1645, card.mcmSetId)
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
