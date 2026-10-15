package io.asv.mtgocr.ocrreader

import android.content.Context
import android.util.Base64
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** One completed account snapshot, separate from inventory. Shared identities and complete histories in both directions. */
internal object CloudBuylistSync {
    private const val MAX_BYTES=64*1024*1024
    private fun head(user: FirebaseUser)=FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("shopping").document("state")
    private fun allowed(c: Context,user: FirebaseUser)=PremiumAccess.isEnabled(c) && CloudLibrarySync.currentUser(c)?.uid==user.uid
    suspend fun publishAccess(c: Context,user: FirebaseUser, ref: DocumentReference = head(user)) {
        if(CloudLibrarySync.currentUser(c)?.uid!=user.uid)return
        ref.set(mapOf("premiumEnabled" to PremiumAccess.isEnabled(c)),SetOptions.merge()).await()
    }
    suspend fun sync(c: Context,user: FirebaseUser, ref: DocumentReference = head(user)) {
        check(allowed(c,user)) { c.getString(R.string.premium_required) }
        val prefs=c.getSharedPreferences("cloud_buylist_sync",Context.MODE_PRIVATE)
        val local=BuylistStore.cloudState(c)
        val localHash=local?.let(CloudSnapshotCodec::sha256)
        val remote=ref.get().await()
        val oldId=remote.getString("activeSnapshotId").orEmpty()
        val remoteHash=remote.getString("sha256").takeUnless { oldId.isBlank() }
        val base=prefs.getString(user.uid,null)
        val localBase=prefs.getString(user.uid+":local",base)
        fun remember(remoteHash: String?, localHash: String?) {
            prefs.edit().putString(user.uid,remoteHash).putString(user.uid+":local",localHash).commit()
        }
        when(CloudBuylistPolicy.action(localHash,remoteHash,base,localBase)) {
            CloudBuylistPolicy.Action.CONFLICT -> error(c.getString(R.string.buy_sync_conflict))
            CloudBuylistPolicy.Action.SAME -> {
                publishAccess(c,user,ref)
                remember(remoteHash,localHash)
            }
            CloudBuylistPolicy.Action.RESTORE -> {
                check(oldId.isNotBlank())
                val snapshot=ref.collection("snapshots").document(oldId)
                val metadata=snapshot.get().await();val count=metadata.getLong("chunkCount")?.toInt() ?: 0
                require(count in 1..160 && metadata.getLong("schemaVersion")==1L && metadata.getString("sha256")==remoteHash)
                val docs=snapshot.collection("chunks").get().await().documents.associateBy { it.id }
                val chunks=(0 until count).map { i -> Base64.decode(requireNotNull(docs[i.toString().padStart(5,'0')]?.getString("data")),Base64.DEFAULT) }
                require(chunks.sumOf { it.size }<=MAX_BYTES)
                val bytes=GZIPInputStream(ByteArrayInputStream(CloudSnapshotCodec.join(chunks))).use { gzip ->
                    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) { val n=gzip.read(buffer);if(n<0)break;require(out.size()+n<=MAX_BYTES);out.write(buffer,0,n) };out.toByteArray()
                }
                require(CloudSnapshotCodec.sha256(bytes)==remoteHash)
                check(allowed(c,user))
                val restoredHash=BuylistStore.restoreCloud(c,bytes,localHash)
                remember(remoteHash,restoredHash);publishAccess(c,user,ref)
            }
            CloudBuylistPolicy.Action.UPLOAD -> {
                val bytes=requireNotNull(local);require(bytes.size<=MAX_BYTES)
                val out=ByteArrayOutputStream();GZIPOutputStream(out).use { it.write(bytes) }
                val chunks=CloudSnapshotCodec.chunks(out.toByteArray())
                val snapshotId=UUID.randomUUID().toString();val snapshot=ref.collection("snapshots").document(snapshotId)
                chunks.forEachIndexed { i,chunk ->
                    check(allowed(c,user))
                    snapshot.collection("chunks").document(i.toString().padStart(5,'0')).set(mapOf("data" to Base64.encodeToString(chunk,Base64.NO_WRAP))).await()
                }
                snapshot.set(mapOf("schemaVersion" to 1,"chunkCount" to chunks.size,"sha256" to localHash,"byteCount" to bytes.size)).await()
                check(allowed(c,user))
                FirebaseFirestore.getInstance().runTransaction { tx ->
                    check(allowed(c,user))
                    check(tx.get(ref).getString("activeSnapshotId").orEmpty()==oldId) { c.getString(R.string.buy_sync_conflict) }
                    tx.set(ref,mapOf("activeSnapshotId" to snapshotId,"sha256" to localHash,"premiumEnabled" to true,"updatedAt" to FieldValue.serverTimestamp()))
                }.await()
                remember(localHash,localHash)
                // Keep the previous snapshot available for readers already fetching it; do not delete in-flight data.
            }
        }
    }
}
