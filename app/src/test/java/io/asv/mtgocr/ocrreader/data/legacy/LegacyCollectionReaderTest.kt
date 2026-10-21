package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.collection.*
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class LegacyCollectionReaderTest {
    private fun card() = CardInfo("Nombre localizado", "2.50 EUR", "Texto", "image", "3").apply {
        printingUuid = "printing-a"
        finish = "foil"
        languageCode = "pt"
        condition = "good"
        groups.add("Grupo")
        decks.add("Mazo")
        sideboardDecks.add("Mazo")
        addScanEvidence("evidence", "photos/test.jpg")
    }

    private fun collection() = Biblio("synthetic.Json", "Synthetic").apply { addCard(card()) }

    private fun read(value: LegacyCollectionLoad, id: String = "library-a") = runBlocking {
        LegacyCollectionReader(LegacyCollectionSource { value }, Dispatchers.Unconfined).read(id)
    }

    private fun snapshot(collection: Biblio) =
        (read(LegacyCollectionLoad.Loaded(collection)) as CollectionReadResult.Ready).snapshot

    @Test fun preservesIdentityAndIndependentVariants() {
        val collection = collection()
        val row = snapshot(collection).rows.single()
        assertEquals(collection.cards.single().collectionItemId, row.id)
        assertEquals("printing-a", row.printingUuid)
        assertEquals("foil", row.finish)
        assertEquals("pt", row.language)
        assertEquals("good", row.condition)
        assertEquals(3, row.quantity)
        assertEquals("Nombre localizado", row.name)
        assertEquals(listOf("Grupo"), row.groups)
        assertEquals(listOf("Mazo"), row.decks)
        assertEquals(listOf("Mazo"), row.sideboardDecks)
    }

    @Test fun doesNotFollowTheActiveLibraryImplicitly() = runBlocking {
        var requested = ""
        val reader = LegacyCollectionReader(LegacyCollectionSource {
            requested = it
            LegacyCollectionLoad.Loaded(collection())
        }, Dispatchers.Unconfined)
        val result = reader.read("explicit-library") as CollectionReadResult.Ready
        assertEquals("explicit-library", requested)
        assertEquals(requested, result.snapshot.libraryId)
    }

    @Test fun sourceMutationsCannotChangePublishedSnapshot() {
        val collection = collection()
        val result = snapshot(collection)
        collection.cards.single().apply { groups.clear(); quantity = "99"; name = "changed" }
        collection.cards.clear()
        assertEquals(3, result.rows.single().quantity)
        assertEquals(listOf("Grupo"), result.rows.single().groups)
        assertEquals("Nombre localizado", result.rows.single().name)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun publishedGroupsCannotBeMutatedThroughJavaList() {
        (snapshot(collection()).rows.single().groups as MutableList<String>).add("changed")
    }

    @Test(expected = UnsupportedOperationException::class)
    fun publishedRowsCannotBeMutatedThroughJavaList() {
        (snapshot(collection()).rows as MutableList<CollectionRow>).clear()
    }

    @Test fun projectionDoesNotChangeSerializedBytes() {
        val collection = collection()
        val before = serialize(collection)
        snapshot(collection)
        assertArrayEquals(before, serialize(collection))
    }

    @Test fun missingIdentityRequiresLegacyUpgradeWithoutMutatingSource() {
        val collection = collection()
        val field = CardInfo::class.java.getDeclaredField("collectionItemId").apply { isAccessible = true }
        field.set(collection.cards.single(), null)
        val before = serialize(collection)
        assertEquals(CollectionReadResult.Failed(CollectionReadFailure.MISSING_IDENTITY),
            read(LegacyCollectionLoad.Loaded(collection)))
        assertArrayEquals(before, serialize(collection))
    }

    @Test fun duplicateIdentityIsNotPublished() {
        val collection = collection()
        collection.addCard(collection.cards.single().snapshotForPersistence())
        assertEquals(CollectionReadResult.Failed(CollectionReadFailure.DUPLICATE_IDENTITY),
            read(LegacyCollectionLoad.Loaded(collection)))
    }

    @Test fun absentFileIsNotAnEmptyOrUnreadableCollection() {
        assertEquals(CollectionReadResult.Missing, read(LegacyCollectionLoad.Missing))
        assertEquals(CollectionReadResult.Failed(CollectionReadFailure.UNREADABLE),
            read(LegacyCollectionLoad.Failed(CollectionReadFailure.UNREADABLE)))
        assertTrue(snapshot(Biblio("empty.Json", "Empty")).rows.isEmpty())
    }

    @Test fun unknownLibraryIsNotSilentlyDefaulted() {
        assertEquals(CollectionReadResult.Failed(CollectionReadFailure.UNKNOWN_LIBRARY),
            read(LegacyCollectionLoad.Failed(CollectionReadFailure.UNKNOWN_LIBRARY)))
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverConvertedToEmptyData() {
        runBlocking {
            LegacyCollectionReader(LegacyCollectionSource { throw CancellationException("cancelled") },
                Dispatchers.Unconfined).read("library")
        }
    }

    @Test fun baselineBinaryFixtureRemainsReadableAndRoundTrips() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/legacy/master-2d8a4ad.ser")).use { it.readBytes() }
        val original = ObjectInputStream(bytes.inputStream()).use { it.readObject() as Biblio }
        val reloaded = ObjectInputStream(serialize(original).inputStream()).use { it.readObject() as Biblio }
        assertEquals("synthetic-baseline.Json", original.nameFile)
        assertEquals("fixture-row", original.cards.single().collectionItemId)
        assertEquals(snapshot(original), snapshot(reloaded))
        assertEquals(listOf("photos/synthetic.jpg"), reloaded.cards.single().scanPhotoPaths)
        assertEquals(listOf("{\"synthetic\":true}"), reloaded.cards.single().scanMetadataHistory)
        assertEquals(-2428653643628406405L, ObjectStreamClass.lookup(Biblio::class.java).serialVersionUID)
        assertEquals(-7899312800426509165L, ObjectStreamClass.lookup(CardInfo::class.java).serialVersionUID)
    }

    private fun serialize(value: Biblio): ByteArray {
        val buffer = ByteArrayOutputStream()
        ObjectOutputStream(buffer).use { it.writeObject(value) }
        return buffer.toByteArray()
    }
}
