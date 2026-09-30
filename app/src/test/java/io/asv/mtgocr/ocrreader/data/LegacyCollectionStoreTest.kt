package io.asv.mtgocr.ocrreader.data

import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LegacyCollectionStoreTest {
    @Test fun languageSavePreservesProvisionalUntilExplicitConfirmation() {
        val collection=Biblio("test","Test")
        val card=LegacyCollectionStore.addCopyToCollection(collection,option("nonfoil"),"")
        card.setProvisionalEdition(true)
        assertSame(card,LegacyCollectionStore.applyReviewedPrintingInCollection(collection,card.snapshotForPersistence(),option("nonfoil"),"en",confirmPrinting=false))
        assertTrue(card.isProvisionalEdition)
        assertEquals("en",card.languageCode)
        assertNull(LegacyCollectionStore.applyReviewedPrintingInCollection(collection,card.snapshotForPersistence(),option("foil"),"en",allowFinishChange=true,confirmPrinting=false))
        assertSame(card,LegacyCollectionStore.applyReviewedPrintingInCollection(collection,card.snapshotForPersistence(),option("nonfoil"),"en",confirmPrinting=true))
        assertFalse(card.isProvisionalEdition)
    }

    @Test fun languageOnlyReviewKeepsPriceForTheSamePrintingAndFinish() {
        val collection=Biblio("test","Test")
        val card=LegacyCollectionStore.addCopyToCollection(collection,option("nonfoil"),"es")
        card.price="9 EUR"; card.priceL="9"; card.priceM="9"; card.priceH="9"
        val expected=card.snapshotForPersistence()
        LegacyCollectionStore.applyReviewedPrintingInCollection(collection,expected,option("nonfoil").copy(price=null),"pt",true)
        assertEquals("9 EUR",card.price)
        assertEquals("9",card.priceM)
        assertEquals("pt",card.languageCode)
    }
    @Test fun explicitEditionSelectionCanChangeFinishWithoutLosingInventory() {
        val collection=Biblio("test","Test")
        val card=LegacyCollectionStore.addCopyToCollection(collection,option("nonfoil"),"es")
        card.quantityCount=2; card.groups.add("Keep")
        card.price="99 EUR"
        val expected=card.snapshotForPersistence()
        val foil=option("foil").copy(price=null)
        assertSame(card,LegacyCollectionStore.applyReviewedPrintingInCollection(collection,expected,foil,"es",allowFinishChange=true))
        assertEquals("foil",card.finish)
        assertEquals("",card.price)
        assertEquals(2,card.quantityCount)
        assertEquals(listOf("Keep"),card.groups)
        assertNull(LegacyCollectionStore.applyReviewedPrintingInCollection(collection,expected,foil,"es",allowFinishChange=true))
    }
    @Test fun edscanRepeatedCopiesHaveIndependentIdentityAndQuantity() {
        val collection = Biblio("test", "Test")
        val printing = option("nonfoil")
        val first = LegacyCollectionStore.addCopyToCollection(collection, printing, "pt", "Cornelius 1", false)
        val second = LegacyCollectionStore.addCopyToCollection(collection, printing, "pt", "Cornelius 1", false)
        assertNotEquals(first.collectionItemId, second.collectionItemId)
        assertEquals(2, collection.cards.size)
        assertEquals(1, first.quantityCount); assertEquals(1, second.quantityCount)
        assertEquals(first.groups, second.groups)
        assertTrue(LegacyCollectionStore.editRecentQuantityInCollection(collection, second.collectionItemId, 0, true))
        assertEquals(listOf(first), collection.cards)
        assertEquals(1, first.quantityCount)
    }

    @Test fun scanPriceOnlyUpdatesMatchingPrintingAndFinish() {
        val collection = Biblio("test", "Test")
        val card = LegacyCollectionStore.addCopyToCollection(collection, option("foil"), "pt")
        card.quantityCount = 4; card.addGroup("Cornelius 17")
        val before = card.snapshotForPersistence()
        assertFalse(LegacyCollectionStore.applyScanPriceToCard(card, "other", "foil", 5.0, "EUR"))
        assertFalse(LegacyCollectionStore.applyScanPriceToCard(card, card.printingUuid, "nonfoil", 5.0, "EUR"))
        assertFalse(LegacyCollectionStore.applyScanPriceToCard(card, card.printingUuid, "foil", Double.NaN, "EUR"))
        assertTrue(LegacyCollectionStore.applyScanPriceToCard(card, card.printingUuid, "foil", 5.0, "EUR"))
        assertEquals("5.00 EUR", card.price)
        assertEquals(before.quantityCount, card.quantityCount)
        assertEquals(before.groups, card.groups)
        assertEquals(before.languageCode, card.languageCode)
    }

    @Test fun reviewedEditionCanExplicitlyReplaceUnsupportedLanguage() {
        val collection=Biblio("test","Test")
        val card=LegacyCollectionStore.addCopyToCollection(collection,option("nonfoil"),"es")
        val expected=card.snapshotForPersistence()
        val changed=LegacyCollectionStore.applyReviewedPrintingInCollection(collection,expected,
            option("nonfoil").copy(printingUuid="english-only"),"en")
        assertSame(card,changed); assertEquals("en",card.languageCode)
        assertEquals(expected.collectionItemId,card.collectionItemId)
        assertEquals(expected.quantityCount,card.quantityCount)
    }

    @Test fun visualReviewChangesIdentityButPreservesCopiesAndGroups() {
        val collection=Biblio("test","Test")
        val card=LegacyCollectionStore.addCopyToCollection(collection,option("foil"),"pt")
        card.quantityCount=4; card.addGroup("Keep")
        val expected=card.snapshotForPersistence()
        val replacement=option("foil").copy(printingUuid="new",displayName="Corrected title",price=null)
        assertSame(card,LegacyCollectionStore.applyReviewedPrintingInCollection(collection,expected,replacement))
        assertEquals("Corrected title",card.name); assertEquals("new",card.printingUuid)
        assertEquals(4,card.quantityCount); assertEquals("pt",card.languageCode)
        assertEquals(listOf("Keep"),card.groups); assertEquals("",card.price)
        assertNull(LegacyCollectionStore.applyReviewedPrintingInCollection(collection,expected,replacement))
    }
    @Test fun visualReviewCannotChangeFinishOrCreateMissingRow() {
        val collection=Biblio("test","Test")
        val card=LegacyCollectionStore.addCopyToCollection(collection,option("nonfoil"),"es")
        val expected=card.snapshotForPersistence()
        assertNull(LegacyCollectionStore.applyReviewedPrintingInCollection(collection,expected,option("foil")))
        card.languageCode="pt"
        assertNull(LegacyCollectionStore.applyReviewedPrintingInCollection(collection,expected,option("nonfoil")))
        collection.cards.clear()
        assertNull(LegacyCollectionStore.applyReviewedPrintingInCollection(collection,expected,option("nonfoil")))
    }

    @Test fun recentEditsUseExactIdentityAndNeverChangeOtherRows() {
        val library = io.asv.mtgocr.ocrreader.model.Biblio("test", "Test")
        val first = io.asv.mtgocr.ocrreader.model.CardInfo("same", "1 EUR", "", "", "2")
        val second = io.asv.mtgocr.ocrreader.model.CardInfo("same", "2 EUR", "", "", "3")
        library.cards.addAll(listOf(first,second))
        org.junit.Assert.assertFalse(LegacyCollectionStore.editRecentQuantityInCollection(library,"missing",-1,true))
        org.junit.Assert.assertTrue(LegacyCollectionStore.editRecentQuantityInCollection(library,first.collectionItemId,1,false))
        org.junit.Assert.assertEquals(3,first.quantityCount)
        org.junit.Assert.assertTrue(LegacyCollectionStore.editRecentQuantityInCollection(library,first.collectionItemId,0,true))
        org.junit.Assert.assertEquals(listOf(second),library.cards)
        org.junit.Assert.assertEquals(3,second.quantityCount)
    }
    @Test fun recentQuantityCannotUnderflowOrOverflow() {
        val library=io.asv.mtgocr.ocrreader.model.Biblio("test","Test")
        val card=io.asv.mtgocr.ocrreader.model.CardInfo("card","","","","1")
        library.cards.add(card)
        org.junit.Assert.assertFalse(LegacyCollectionStore.editRecentQuantityInCollection(library,card.collectionItemId,-1,false))
        card.quantityCount=Int.MAX_VALUE
        org.junit.Assert.assertFalse(LegacyCollectionStore.editRecentQuantityInCollection(library,card.collectionItemId,1,false))
    }

    @Test fun corneliusCopiesStayInOwnGroupAndDoNotGuessUnknownLanguage() {
        val collection = Biblio("test", "Test")
        val printing = option("nonfoil")
        val old = LegacyCollectionStore.addCopyToCollection(collection, printing, "en")
        old.quantityCount = 7
        old.addGroup("Existing")
        val first = LegacyCollectionStore.addCopyToCollection(collection, printing, "", "Cornelius 1")
        val second = LegacyCollectionStore.addCopyToCollection(collection, printing, "", "Cornelius 1")
        val anotherSession = LegacyCollectionStore.addCopyToCollection(collection, printing, "", "Cornelius 2")
        val pt = LegacyCollectionStore.addCopyToCollection(collection, printing, "pt", "Cornelius 1")
        assertSame(first, second)
        assertEquals(2, first.quantityCount)
        assertEquals("", first.languageCode)
        assertEquals(listOf("Cornelius 1"), first.groups)
        assertNotEquals(first.collectionItemId, anotherSession.collectionItemId)
        assertNotEquals(first.collectionItemId, pt.collectionItemId)
        assertEquals(7, old.quantityCount)
        assertEquals(listOf("Existing"), old.groups)
    }
    @Test fun batchCorrectionChangesFirstAndNewCopiesButNotOldInventoryOrExistingDestination() {
        val collection = Biblio("test", "Test")
        val original = option("nonfoil")
        val selected = original.copy(printingUuid = "TSB", setCode = "TSB")
        val old = LegacyCollectionStore.addCopiesToCollection(collection, original, "pt", 10)
        val existingDestination = LegacyCollectionStore.addCopiesToCollection(collection, selected, "pt", 5)
        old.addScanEvidence("old", "old.jpg")
        LegacyCollectionStore.addCopyToCollection(collection, original, "pt")
        old.addScanEvidence("first", "first.jpg")
        old.groups.add("Group")
        val result = LegacyCollectionStore.correctBatchInCollection(collection, old.collectionItemId, original, selected, 1, 4)
        assertSame(old, result.originalRemaining)
        assertEquals(10, old.quantityCount)
        assertEquals("printing-1", old.printingUuid)
        assertEquals(5, existingDestination.quantityCount)
        assertEquals(4, result.card.quantityCount)
        assertEquals("TSB", result.card.printingUuid)
        assertNotEquals(old.collectionItemId, result.card.collectionItemId)
        assertNotEquals(existingDestination.collectionItemId, result.card.collectionItemId)
        assertEquals("pt", result.card.languageCode)
        assertEquals(listOf("Group"), result.card.groups)
        assertEquals(listOf("old.jpg"), old.scanPhotoPaths)
        assertEquals(listOf("first.jpg"), result.card.scanPhotoPaths)
        assertEquals(listOf("first"), result.card.scanMetadataHistory)
        result.card.groups.add("New")
        assertEquals(listOf("Group"), old.groups)
        assertSame(result.card, LegacyCollectionStore.addCopiesToCollection(collection, selected, "pt", 2, result.card.collectionItemId))
        assertEquals(6, result.card.quantityCount)
        assertEquals(5, existingDestination.quantityCount)
        assertEquals(10, old.quantityCount)
    }

    @Test fun extraCopiesNeverFallbackToAnotherRowIfBatchIdDisappeared() {
        val collection = Biblio("test", "Test")
        val original = option("nonfoil")
        val card = LegacyCollectionStore.addCopyToCollection(collection, original, "en")
        try {
            LegacyCollectionStore.addCopiesToCollection(collection, original, "en", 2, "missing")
            fail("Missing ID must not modify a different row")
        } catch (_: IllegalStateException) { }
        assertEquals(1, card.quantityCount)
    }

    @Test fun batchCorrectionWithoutOldCopiesKeepsIdAndWorksWithNoExtraCopies() {
        val collection = Biblio("test", "Test")
        val original = option("nonfoil")
        val card = LegacyCollectionStore.addCopiesToCollection(collection, original, "pt", 3)
        card.name = "Nome impresso"
        card.addScanEvidence("scan", "photo")
        val id = card.collectionItemId
        val selected = original.copy(printingUuid = "new", price = null)
        val result = LegacyCollectionStore.correctBatchInCollection(collection, id, original, selected, 3, 3)
        assertNull(result.originalRemaining)
        assertEquals(id, result.card.collectionItemId)
        assertEquals(3, card.quantityCount)
        assertEquals(1, collection.cards.size)
        assertEquals("Nome impresso", card.name)
        assertEquals("pt", card.languageCode)
        assertEquals("", card.price)
        assertEquals(listOf("photo"), card.scanPhotoPaths)
    }

    @Test fun batchCorrectionMovesEveryPreviouslySavedBatchCopyAndLeavesOlderHistory() {
        val collection = Biblio("test", "Test")
        val original = option("nonfoil")
        val card = LegacyCollectionStore.addCopiesToCollection(collection, original, "en", 7)
        repeat(7) { card.addScanEvidence("scan$it", "$it.jpg") }
        val result = LegacyCollectionStore.correctBatchInCollection(collection, card.collectionItemId, original,
            original.copy(printingUuid = "new"), 3, 5)
        assertEquals(4, card.quantityCount)
        assertEquals(5, result.card.quantityCount)
        assertEquals(listOf("4.jpg", "5.jpg", "6.jpg"), result.card.scanPhotoPaths)
        assertEquals(listOf("0.jpg", "1.jpg", "2.jpg", "3.jpg"), card.scanPhotoPaths)
    }

    @Test fun batchCorrectionRejectsStalePrintingBeforeMutatingCollection() {
        val collection = Biblio("test", "Test")
        val original = option("nonfoil")
        val card = LegacyCollectionStore.addCopiesToCollection(collection, original, "en", 2)
        try {
            LegacyCollectionStore.correctBatchInCollection(collection, card.collectionItemId,
                original.copy(printingUuid = "stale"), original, 1, 3)
            fail("Stale source should fail")
        } catch (_: IllegalStateException) { }
        assertEquals(2, card.quantityCount)
        assertEquals(1, collection.cards.size)
    }

    @Test fun editionCorrectionPreservesIdentityCopiesLanguageGroupsAndEvidence() {
        val collection = Biblio("test", "Test")
        val card = LegacyCollectionStore.addCopiesToCollection(collection, option("nonfoil"), "pt", 4)
        card.name = "Nome impresso"
        card.condition = "LP"
        card.groups.add("Grupo")
        card.addScanEvidence("evidence", "photo.jpg")
        val id = card.collectionItemId
        val replacement = option("foil").copy(printingUuid = "new", setCode = "SPG", collectorNumber = "150", price = null)
        assertSame(card, LegacyCollectionStore.updateEditionInCollection(collection, id, replacement))
        assertEquals(id, card.collectionItemId)
        assertEquals(1, collection.cards.size)
        assertEquals(4, card.quantityCount)
        assertEquals("pt", card.languageCode)
        assertEquals("Nome impresso", card.name)
        assertEquals("light_played", card.condition)
        assertEquals(listOf("Grupo"), card.groups)
        assertEquals(listOf("evidence"), card.scanMetadataHistory)
        assertEquals(listOf("photo.jpg"), card.scanPhotoPaths)
        assertEquals("new", card.printingUuid)
        assertEquals("SPG", card.setCode)
        assertEquals("foil", card.finish)
        assertEquals("", card.price)
        assertEquals("", card.priceM)
    }

    @Test fun editionCorrectionUsesStableIdAndNeverCreatesMissingRow() {
        val collection = Biblio("test", "Test")
        val one = LegacyCollectionStore.addCopyToCollection(collection, option("nonfoil"))
        val two = LegacyCollectionStore.addCopyToCollection(collection, option("foil"))
        val replacement = option("nonfoil").copy(printingUuid = "replacement")
        assertNull(LegacyCollectionStore.updateEditionInCollection(collection, "missing", replacement))
        assertSame(two, LegacyCollectionStore.updateEditionInCollection(collection, two.collectionItemId, replacement))
        assertEquals("printing-1", one.printingUuid)
        assertEquals(2, collection.cards.size)
    }

    @Test fun batchAddsOnlyExtraCopiesAndPreservesExistingLanguageAndFinishGroups() {
        val collection = Biblio("test", "Test")
        val normal = option(finish = "nonfoil")
        val previous = LegacyCollectionStore.addCopiesToCollection(collection, normal, "en", 7)
        val spanish = LegacyCollectionStore.addCopiesToCollection(collection, normal, "es", 2)
        val foil = LegacyCollectionStore.addCopiesToCollection(collection, option(finish = "foil"), "en", 2)
        LegacyCollectionStore.addCopyToCollection(collection, normal, "en") // first scan
        val result = LegacyCollectionStore.addCopiesToCollection(collection, normal, "en", 3) // total batch 4
        assertSame(previous, result)
        assertEquals(11, result.quantityCount)
        assertEquals(2, spanish.quantityCount)
        assertEquals(2, foil.quantityCount)
        assertEquals(3, collection.cards.size)
    }
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

    @Test
    fun addCopy_keepsDifferentPhysicalLanguagesAsSeparateRows() {
        val collection = Biblio("myBiblio.Json", "Test")
        collection.addCard(CardInfo("Mox Opal", "", "", "", "1").apply {
            printingUuid = "printing-1"
            finish = "nonfoil"
            languageCode = "en"
        })

        val italian = LegacyCollectionStore.addCopyToCollection(
            collection,
            option(finish = "nonfoil"),
            languageCode = "it"
        )

        assertEquals(2, collection.cards.size)
        assertEquals("it", italian.languageCode)
        assertEquals(1, italian.quantityCount)
    }

    @Test
    fun addCopy_incrementsTheSamePrintingAndPhysicalLanguage() {
        val collection = Biblio("myBiblio.Json", "Test")
        val italian = CardInfo("Mox Opal", "", "", "", "1").apply {
            printingUuid = "printing-1"
            finish = "nonfoil"
            languageCode = "it"
        }
        collection.addCard(italian)

        val result = LegacyCollectionStore.addCopyToCollection(
            collection,
            option(finish = "nonfoil"),
            languageCode = "it"
        )

        assertSame(italian, result)
        assertEquals(2, result.quantityCount)
        assertEquals(1, collection.cards.size)
    }

    @Test fun observedPortugueseCreatesSeparateCopiesFromSpanishWithoutChangingOldLanguage() {
        val collection = Biblio("test", "Test")
        val printing = option("nonfoil").copy(cardName = "Flickering Spirit", displayName = "Espírito Flutuante")
        val spanish = LegacyCollectionStore.addCopyToCollection(collection, printing, "es")
        val portuguese = LegacyCollectionStore.addCopyToCollection(collection, printing, "pt")
        LegacyCollectionStore.addCopyToCollection(collection, printing, "pt")
        assertEquals(2, collection.cards.size)
        assertEquals("es", spanish.languageCode)
        assertEquals(1, spanish.quantityCount)
        assertEquals("pt", portuguese.languageCode)
        assertEquals(2, portuguese.quantityCount)
        assertEquals(printing.printingUuid, portuguese.printingUuid)
        assertNotEquals(spanish.collectionItemId, portuguese.collectionItemId)
    }

    @Test fun reviewDeleteRefusesChangedRowAndKeepsOtherRows() {
        val collection=Biblio("test","Test")
        val card=LegacyCollectionStore.addCopyToCollection(collection,option("nonfoil"),"es")
        val other=LegacyCollectionStore.addCopyToCollection(collection,option("foil"),"es")
        val expected=card.snapshotForPersistence()
        card.quantityCount=2
        assertFalse(LegacyCollectionStore.editRecentQuantityInCollection(collection,card.collectionItemId,0,true,expected))
        assertEquals(2,collection.cards.size)
        assertTrue(LegacyCollectionStore.editRecentQuantityInCollection(collection,card.collectionItemId,0,true,card.snapshotForPersistence()))
        assertEquals(listOf(other),collection.cards)
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
