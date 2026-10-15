package io.asv.mtgocr.ocrreader

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/** Explicit opt-in: uploads the user's existing lists, never inserts synthetic inventory. */
class ShoppingCloudDeviceTest {
    @Test fun existingListsAndAllHistoryRoundtrip() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyShoppingCloud")=="true")
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(PremiumAccess.isEnabled(c))
        val user=requireNotNull(CloudLibrarySync.currentUser(c))
        val before=requireNotNull(BuylistStore.cloudState(c))
        CloudBuylistSync.sync(c,user)
        val head=FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("shopping").document("state")
        val state=head.get().await()
        assertEquals(true,state.getBoolean("premiumEnabled"))
        assertEquals(CloudSnapshotCodec.sha256(before),state.getString("sha256"))
        val snapshot=head.collection("snapshots").document(requireNotNull(state.getString("activeSnapshotId")))
        val count=requireNotNull(snapshot.get().await().getLong("chunkCount")).toInt()
        val parts=snapshot.collection("chunks").get().await().documents.associateBy { it.id }
        val compressed=CloudSnapshotCodec.join((0 until count).map { Base64.decode(parts.getValue(it.toString().padStart(5,'0')).getString("data"),Base64.DEFAULT) })
        val restored=GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        assertArrayEquals(before,restored)
        assertArrayEquals(before,BuylistStore.cloudState(c))
        assertEquals(BuylistStore.decodeAll(before),BuylistStore.decodeAll(restored))
    }
}
