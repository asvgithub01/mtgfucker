package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSnapshotCodecTest {
    @Test
    fun roundTripPreservesLegacyCollectionFields() {
        val collection = Biblio("myBiblio.Json", "Biblio principal")
        val card = CardInfo("Black Lotus", "1.00 EUR", "Artifact", "https://image", "2").apply {
            printingUuid = "printing-1"
            setCode = "LEA"
            finish = "nonfoil"
            languageCode = "en"
        }
        collection.addCard(card)

        val bytes = CloudSnapshotCodec.encode(collection)
        val restored = CloudSnapshotCodec.decode(CloudSnapshotCodec.join(CloudSnapshotCodec.chunks(bytes, 17)))

        assertTrue(bytes.isNotEmpty())
        assertEquals("Biblio principal", restored.name)
        assertEquals("Black Lotus", restored.cards.single().name)
        assertEquals(2, restored.cards.single().quantityCount)
        assertEquals("printing-1", restored.cards.single().printingUuid)
    }

    @Test
    fun mergeKeepsRemoteOnlyCardsAndLetsLocalVersionWin() {
        val remote = Biblio("myBiblio.Json", "old")
        val sharedRemote = CardInfo("Remote name", "", "", "", "1")
        val remoteOnly = CardInfo("Cloud only", "", "", "", "1")
        val idField = CardInfo::class.java.getDeclaredField("collectionItemId").apply { isAccessible = true }
        idField.set(sharedRemote, "shared")
        idField.set(remoteOnly, "remote")
        remote.addCard(sharedRemote)
        remote.addCard(remoteOnly)

        val local = Biblio("myBiblio.Json", "new")
        val sharedLocal = CardInfo("Local name", "", "", "", "3")
        idField.set(sharedLocal, "shared")
        local.addCard(sharedLocal)

        val merged = CloudSnapshotCodec.merge(remote, local, "target.Json", "Target")

        assertEquals(2, merged.cards.size)
        assertEquals("Local name", merged.cards.first { it.collectionItemId == "shared" }.name)
        assertEquals("Cloud only", merged.cards.first { it.collectionItemId == "remote" }.name)
    }
}
