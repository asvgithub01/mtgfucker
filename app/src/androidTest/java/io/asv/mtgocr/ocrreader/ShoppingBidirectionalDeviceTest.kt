package io.asv.mtgocr.ocrreader

import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream

/** Isolated files/preferences AND separate ephemeral cloud path. Never changes real shopping or inventory. */
class ShoppingBidirectionalDeviceTest {
    @Test fun webEditsRestoreWithoutDuplicatesOrHistoryLossAndConflictsNeverOverwrite() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyShoppingCloud")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val user=requireNotNull(CloudLibrarySync.currentUser(app))
        val tag="shopping-roundtrip-${UUID.randomUUID()}"
        val dir=File(app.cacheDir,tag).apply { mkdirs() }
        val context=object:ContextWrapper(app) {
            override fun getFilesDir()=dir
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("$tag-$name",mode)
        }
        context.getSharedPreferences("premium_access",0).edit().putBoolean("premium_enabled",true).commit()
        context.getSharedPreferences("cloud_library_sync",0).edit().putBoolean("auto_sync_enabled",false).commit()
        val ref=FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("shoppingTests").document(tag)
        suspend fun publishWeb(lists:List<BuyList>):String {
            // Pretty JSON deliberately differs from Android's canonical serialization.
            val bytes=JSONObject().put("lists",JSONArray(lists.map(BuylistStore::encode))).put("version",1).toString(2).toByteArray()
            val hash=CloudSnapshotCodec.sha256(bytes);val id=UUID.randomUUID().toString();val snap=ref.collection("snapshots").document(id)
            val out=ByteArrayOutputStream();GZIPOutputStream(out).use { it.write(bytes) }
            val chunks=CloudSnapshotCodec.chunks(out.toByteArray())
            chunks.forEachIndexed { i,b -> snap.collection("chunks").document(i.toString().padStart(5,'0')).set(mapOf("data" to Base64.encodeToString(b,Base64.NO_WRAP))).await() }
            snap.set(mapOf("schemaVersion" to 1,"chunkCount" to chunks.size,"sha256" to hash,"byteCount" to bytes.size)).await()
            ref.set(mapOf("activeSnapshotId" to id,"sha256" to hash,"premiumEnabled" to true)).await()
            return id
        }
        try {
            val entry=BuyEntry(uuid=UUID.randomUUID().toString(),name="Isolated QA",set="FRA",number="1",finish="nonfoil",image="",mcmId="")
            val created=BuylistStore.create(context,"Isolated QA",listOf(entry))
            val initial=BuylistStore.update(context,created.id) { it.copy(history=listOf(1L,2L).map { at->BuySnapshot(at,BuyPolicy.composition(it.entries),listOf(BuyQuote(entry.id,10.0,"2026-10-15"))) }) }
            CloudBuylistSync.sync(context,user,ref)
            val edited=initial.copy(name="Edited on web",entries=listOf(entry.copy(quantity=3)),history=initial.history+BuySnapshot(3,BuyPolicy.composition(listOf(entry.copy(quantity=3))),listOf(BuyQuote(entry.id,30.0,"2026-10-15"))))
            val remoteId=publishWeb(listOf(edited))
            CloudBuylistSync.sync(context,user,ref)
            assertEquals(listOf(edited),BuylistStore.read(context))
            CloudBuylistSync.sync(context,user,ref)
            assertEquals(remoteId,ref.get().await().getString("activeSnapshotId"))
            BuylistStore.update(context,edited.id) { it.copy(name="Edited on Android") }
            CloudBuylistSync.sync(context,user,ref)
            assertNotEquals(remoteId,ref.get().await().getString("activeSnapshotId"))
            assertEquals(initial.history,BuylistStore.read(context).single().history.take(2))
            val base=BuylistStore.read(context).single()
            BuylistStore.update(context,base.id) { it.copy(name="Concurrent local") }
            val concurrent=publishWeb(listOf(base.copy(name="Concurrent web")))
            assertTrue(runCatching { CloudBuylistSync.sync(context,user,ref) }.isFailure)
            assertEquals("Concurrent local",BuylistStore.read(context).single().name)
            assertEquals(concurrent,ref.get().await().getString("activeSnapshotId"))
            // Restore the unchanged base locally to test intentional remote deletion.
            BuylistStore.update(context,base.id) { base }
            val deletion=publishWeb(emptyList())
            CloudBuylistSync.sync(context,user,ref)
            assertTrue(BuylistStore.read(context).isEmpty())
            CloudBuylistSync.sync(context,user,ref)
            assertEquals(deletion,ref.get().await().getString("activeSnapshotId"))
        } finally {
            for(snap in ref.collection("snapshots").get().await()) {
                for(chunk in snap.reference.collection("chunks").get().await())chunk.reference.delete().await()
                snap.reference.delete().await()
            }
            ref.delete().await()
            dir.deleteRecursively()
            for(name in listOf("premium_access","cloud_library_sync","cloud_sync","cloud_buylist_sync"))context.getSharedPreferences(name,0).edit().clear().commit()
        }
    }
}
