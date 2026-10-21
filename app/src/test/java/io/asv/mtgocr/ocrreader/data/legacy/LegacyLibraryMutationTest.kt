package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.library.*
import io.asv.mtgocr.ocrreader.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class LegacyLibraryMutationTest {
    private fun source() = Biblio("synthetic.Json", "Synthetic").apply {
        repeat(2) { index -> addCard(CardInfo("Local $index", "2.00 EUR", "Rules", "image", "3").apply {
            printingUuid = "printing-$index"; finish = "etched"; languageCode = "pt"
            condition = "good"; mcmId = "123"; groups.add("Group"); decks.add("Group")
            sideboardDecks.add("Group"); addScanEvidence("metadata", "photo-$index.jpg")
        }) }
    }
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { stream ->
        ObjectOutputStream(stream).use { it.writeObject(value) }
    }.toByteArray()
    private fun ready(source: Biblio, command: LibraryMutation) =
        (LegacyLibraryMutation.prepare(source, command) as PreparedLibraryMutation.Ready)
    private fun rejection(source: Biblio, command: LibraryMutation, reason: MutationFailure) {
        val before = bytes(source)
        assertEquals(PreparedLibraryMutation.Rejected(reason), LegacyLibraryMutation.prepare(source, command))
        assertArrayEquals(before, bytes(source))
    }

    @Test fun editChangesOnlyRequestedFieldsOnDetachedCompleteGraph() {
        val source = source(); val before = bytes(source); val card = source.cards.first()
        val result = ready(source, LibraryMutation.EditCard(card.collectionItemId, 8, "near_mint"))
        val expected = source.snapshotForPersistence().apply {
            cards.first().quantityCount = 8; cards.first().condition = "near_mint"
        }
        assertArrayEquals(bytes(expected), bytes(result.collection))
        assertArrayEquals(before, bytes(source))
        assertTrue(result.deletedIds.isEmpty())
        assertNotSame(card, result.collection.cards.first())
    }

    @Test fun deleteRequiresConfirmationAndRemovesOnlyExactIdentity() {
        val source = source(); val id = source.cards.first().collectionItemId
        rejection(source, LibraryMutation.DeleteCard(id, false), MutationFailure.CONFIRMATION_REQUIRED)
        val result = ready(source, LibraryMutation.DeleteCard(id, true))
        assertEquals(setOf(id), result.deletedIds)
        assertArrayEquals(bytes(source.cards.last()), bytes(result.collection.cards.single()))
        assertEquals(2, source.cards.size)
    }

    @Test fun zeroAndUnknownConditionAreRejectedRatherThanNormalizedOrDeleted() {
        val source = source(); val id = source.cards.first().collectionItemId
        rejection(source, LibraryMutation.EditCard(id, 0, "good"), MutationFailure.INVALID_INPUT)
        rejection(source, LibraryMutation.EditCard(id, 3, "typo"), MutationFailure.INVALID_INPUT)
        rejection(source, LibraryMutation.EditCard("missing", 3, "good"), MutationFailure.MISSING_CARD)
    }

    @Test fun groupRenameAndDeleteNeverTouchDecksSideboardOrCards() {
        val source = source(); val before = bytes(source)
        val renamed = ready(source, LibraryMutation.RenameGroup("Group", "Renamed")).collection
        assertTrue(renamed.cards.all { it.groups == listOf("Renamed") && it.decks == listOf("Group") &&
            it.sideboardDecks == listOf("Group") && it.quantityCount == 3 })
        rejection(renamed, LibraryMutation.DeleteGroup("Renamed", false), MutationFailure.CONFIRMATION_REQUIRED)
        val result = ready(renamed, LibraryMutation.DeleteGroup("Renamed", true))
        assertTrue(result.deletedIds.isEmpty())
        val expected = source.snapshotForPersistence().apply { cards.forEach { it.groups.clear() } }
        assertArrayEquals(bytes(expected), bytes(result.collection))
        assertArrayEquals(before, bytes(source))
    }

    @Test fun assignmentIsIdempotentAndInvalidSelectionIsAtomic() {
        val source = source(); val id = source.cards.first().collectionItemId
        val command = LibraryMutation.AssignGroup("New", setOf(id))
        val first = ready(source, command).collection
        assertArrayEquals(bytes(first), bytes(ready(first, command).collection))
        assertEquals(listOf("Group"), first.cards.last().groups)
        rejection(source, LibraryMutation.AssignGroup("New", setOf(id, "missing")), MutationFailure.MISSING_CARD)
        rejection(source, LibraryMutation.AssignGroup("New", emptySet()), MutationFailure.INVALID_INPUT)
        val removed = ready(first, LibraryMutation.RemoveFromGroup("New", setOf(id))).collection
        assertArrayEquals(bytes(source), bytes(removed))
    }

    @Test fun renameCollisionDoesNotMergeGroups() {
        val source = source().apply { cards.last().addGroup("Other") }
        rejection(source, LibraryMutation.RenameGroup("Group", "Other"), MutationFailure.DUPLICATE_GROUP)
        rejection(source, LibraryMutation.RenameGroup("absent", "Other"), MutationFailure.MISSING_GROUP)
    }

    @Test fun duplicateOrMissingIdentityRejectsWithoutRepair() {
        val source = source().apply { cards.add(cards.first().snapshotForPersistence()) }
        rejection(source, LibraryMutation.DeleteGroup("Group", true), MutationFailure.CORRUPT_COLLECTION)
        source.cards.removeAt(2)
        CardInfo::class.java.getDeclaredField("collectionItemId").apply { isAccessible = true }.set(source.cards[0], null)
        rejection(source, LibraryMutation.DeleteGroup("Group", true), MutationFailure.CORRUPT_COLLECTION)
    }

    @Test fun editingOneRowPreservesHistoricalNullsAndDescriptionsOnOtherRows() {
        val source = source()
        val untouched = source.cards.last()
        listOf("personalCollections", "scanMetadataHistory", "decks").forEach {
            CardInfo::class.java.getDeclaredField(it).apply { isAccessible = true }.set(untouched, null)
        }
        untouched.lstDescription.add(DescriptionMtgInfo().apply {
            name = "Localized"; description = "Original"; languague = "pt"; imgPath = "legacy.jpg"
        })
        val expected = bytes(untouched)
        val result = ready(source, LibraryMutation.EditCard(source.cards.first().collectionItemId, 4, "good"))
        assertArrayEquals(expected, bytes(result.collection.cards.last()))
        val renamed = ready(source, LibraryMutation.RenameGroup("Group", "Renamed"))
        assertArrayEquals(expected, bytes(renamed.collection.cards.last()))
        val deleted = ready(source, LibraryMutation.DeleteGroup("Group", true))
        assertArrayEquals(expected, bytes(deleted.collection.cards.last()))
    }

    @Test fun baselineFixtureRetainsEveryUneditedFieldAfterMutationAndRoundTrip() {
        val resource = javaClass.getResourceAsStream("/legacy/master-2d8a4ad.ser")
        // Same compatibility fixture used by the read-only reader tests.
        assertNotNull(resource)
        val source = ObjectInputStream(resource).use { it.readObject() as Biblio }
        val card = source.cards.first()
        val result = ready(source, LibraryMutation.EditCard(card.collectionItemId, card.quantityCount + 1, card.condition))
        result.collection.cards.first().quantity = card.quantity
        assertArrayEquals(bytes(source), bytes(result.collection))
    }
}
