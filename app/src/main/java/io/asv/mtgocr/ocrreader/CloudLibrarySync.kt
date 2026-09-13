package io.asv.mtgocr.ocrreader

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import io.asv.mtgocr.ocrreader.model.Biblio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class CloudSyncResult(
    val restoredLibraries: Int = 0,
    val uploadedLibraries: Int = 0,
    val syncedAtMillis: Long = 0L,
    val backupAtMillis: Long = 0L
)

/**
 * Premium-only last-snapshot synchronization.
 *
 * Disabling Premium never deletes Firestore data. Local saves made while paused remain local and
 * are uploaded when Premium returns. On a fresh install an empty/missing local Biblio is restored
 * from the authenticated user's last completed snapshot.
 */
object CloudLibrarySync {
    private const val TAG = "CloudLibrarySync"
    private const val PREFERENCES = "cloud_library_sync"
    private const val KEY_LAST_SYNC = "last_sync_"
    private const val KEY_LAST_BACKUP = "last_backup_"
    private const val KEY_HASH = "hash_"
    private const val KEY_SNAPSHOT = "snapshot_"
    private const val UPLOAD_DELAY_MS = 1_500L
    private const val MAX_BATCH_OPERATIONS = 400

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingUploads = ConcurrentHashMap<String, Job>()
    private val syncMutex = Mutex()
    private val suppressUpload = AtomicInteger(0)

    @JvmStatic
    fun isConfigured(context: Context): Boolean = firebaseApp(context) != null

    @JvmStatic
    fun currentUser(context: Context): FirebaseUser? =
        if (firebaseApp(context) == null) null else runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    @JvmStatic
    fun lastSyncMillis(context: Context): Long {
        val uid = currentUser(context)?.uid ?: return 0L
        return preferences(context).getLong(KEY_LAST_BACKUP + uid, 0L)
    }

    @JvmStatic
    fun onPremiumChanged(context: Context, enabled: Boolean) {
        if (!enabled) {
            pendingUploads.values.forEach { it.cancel() }
            pendingUploads.clear()
            return
        }
        if (currentUser(context) != null) {
            scope.launch { runCatching { reconcile(context.applicationContext) }.onFailure(::logFailure) }
        }
    }

    /** Called only after the atomic local file replacement succeeded. */
    @JvmStatic
    fun onLocalCollectionSaved(context: Context, collection: Biblio) {
        if (suppressUpload.get() > 0 || !PremiumAccess.isEnabled(context)) return
        val user = currentUser(context) ?: return
        val library = LibraryCatalog.libraryForFile(context, collection.nameFile) ?: return
        val key = "${user.uid}:${library.id}"
        pendingUploads.remove(key)?.cancel()
        val snapshot = collection.snapshotForPersistence()
        pendingUploads[key] = scope.launch {
            delay(UPLOAD_DELAY_MS)
            runCatching {
                syncMutex.withLock {
                    val current = DataUtils.readSerializable<Biblio>(context, library.fileName)
                    if (current != null && sameContents(current, snapshot)) {
                        upload(context.applicationContext, user, library, snapshot)
                    }
                }
            }
                .onFailure(::logFailure)
            pendingUploads.remove(key)
        }
    }

    suspend fun reconcile(context: Context): CloudSyncResult = syncMutex.withLock {
        require(PremiumAccess.isEnabled(context)) { "La sincronización está pausada porque Premium no está activo." }
        val user = requireUser(context)
        cancelPendingUploads(user.uid)
        val remoteHeads = libraries(user).get().await().documents.associateBy { it.id }
        val newestRemoteBackup = remoteHeads.values.maxOfOrNull { it.getLong("clientUpdatedAt") ?: 0L } ?: 0L
        var restored = 0
        var uploaded = 0
        val localLibraries = LibraryCatalog.libraries(context).associateBy { it.id }.toMutableMap()

        for ((libraryId, head) in remoteHeads) {
            val remote = download(head.reference)
            val remoteName = head.getString("name").orEmpty().ifBlank { remote.name }
            val library = LibraryCatalog.restoreDefinition(context, libraryId, remoteName)
            localLibraries[library.id] = library
            val localFile = File(context.filesDir, library.fileName)
            val local = DataUtils.readSerializable<Biblio>(context, library.fileName)
            val remoteHash = head.getString("sha256").orEmpty()
            val remoteSnapshot = head.getString("activeSnapshotId").orEmpty()
            val savedHash = state(context, user.uid, library.id, KEY_HASH)
            val savedSnapshot = state(context, user.uid, library.id, KEY_SNAPSHOT)
            val localHash = local?.let { CloudSnapshotCodec.sha256(CloudSnapshotCodec.encode(it)) }.orEmpty()

            when {
                !localFile.isFile || local == null || (local.cards.isNullOrEmpty() && !remote.cards.isNullOrEmpty()) -> {
                    saveRestored(context, library, remote)
                    rememberSnapshot(context, user.uid, library.id, remoteHash, remoteSnapshot)
                    restored++
                }
                savedHash.isBlank() && localHash != remoteHash -> {
                    val merged = CloudSnapshotCodec.merge(remote, local, library.fileName, library.name)
                    saveRestored(context, library, merged)
                    upload(context, user, library, merged)
                    uploaded++
                }
                savedHash.isBlank() -> {
                    rememberSnapshot(context, user.uid, library.id, remoteHash, remoteSnapshot)
                }
                localHash != savedHash && remoteSnapshot != savedSnapshot -> {
                    val merged = CloudSnapshotCodec.merge(remote, local, library.fileName, library.name)
                    saveRestored(context, library, merged)
                    upload(context, user, library, merged)
                    uploaded++
                }
                localHash != savedHash -> {
                    upload(context, user, library, local)
                    uploaded++
                }
                remoteSnapshot != savedSnapshot -> {
                    saveRestored(context, library, remote)
                    rememberSnapshot(context, user.uid, library.id, remoteHash, remoteSnapshot)
                    restored++
                }
            }
        }

        for ((id, library) in localLibraries) {
            if (id in remoteHeads) continue
            val local = DataUtils.readSerializable<Biblio>(context, library.fileName) ?: continue
            upload(context, user, library, local)
            uploaded++
        }
        val now = recordLastSync(context, user.uid)
        if (uploaded == 0 && newestRemoteBackup > 0L) recordCloudBackup(context, user.uid, newestRemoteBackup)
        CloudSyncResult(restored, uploaded, now, lastSyncMillis(context))
    }

    suspend fun restoreLastCloudCopy(context: Context): CloudSyncResult = syncMutex.withLock {
        require(PremiumAccess.isEnabled(context)) { "La sincronización está pausada porque Premium no está activo." }
        val user = requireUser(context)
        cancelPendingUploads(user.uid)
        val heads = libraries(user).get().await().documents
        require(heads.isNotEmpty()) { "Esta cuenta todavía no tiene ninguna copia en la nube." }
        heads.forEach { head ->
            val collection = download(head.reference)
            val library = LibraryCatalog.restoreDefinition(
                context,
                head.id,
                head.getString("name").orEmpty().ifBlank { collection.name }
            )
            saveRestored(context, library, collection)
            rememberSnapshot(
                context,
                user.uid,
                library.id,
                head.getString("sha256").orEmpty(),
                head.getString("activeSnapshotId").orEmpty()
            )
        }
        val now = recordLastSync(context, user.uid)
        val backupAt = heads.maxOfOrNull { it.getLong("clientUpdatedAt") ?: 0L } ?: now
        recordCloudBackup(context, user.uid, backupAt)
        CloudSyncResult(restoredLibraries = heads.size, syncedAtMillis = now, backupAtMillis = backupAt)
    }

    private suspend fun upload(
        context: Context,
        user: FirebaseUser,
        library: LibraryInfo,
        collection: Biblio
    ) {
        if (!PremiumAccess.isEnabled(context)) return
        val bytes = withContext(Dispatchers.Default) { CloudSnapshotCodec.encode(collection) }
        val hash = CloudSnapshotCodec.sha256(bytes)
        val chunks = CloudSnapshotCodec.chunks(bytes)
        val clientUpdatedAt = System.currentTimeMillis()
        val snapshotId = "$clientUpdatedAt-${UUID.randomUUID()}"
        val head = libraries(user).document(library.id)
        val previousSnapshotId = runCatching {
            head.get().await().getString("activeSnapshotId").orEmpty()
        }.getOrDefault("")
        val snapshot = head.collection("snapshots").document(snapshotId)

        chunks.mapIndexed { index, chunk ->
            snapshot.collection("chunks").document(index.toString().padStart(5, '0')) to
                mapOf("data" to Base64.encodeToString(chunk, Base64.NO_WRAP), "index" to index)
        }.chunked(MAX_BATCH_OPERATIONS).forEach { writes ->
            val batch = database().batch()
            writes.forEach { (reference, data) -> batch.set(reference, data) }
            batch.commit().await()
        }
        snapshot.set(
            mapOf(
                "schemaVersion" to CloudSnapshotCodec.SCHEMA_VERSION,
                "chunkCount" to chunks.size,
                "byteCount" to bytes.size,
                "sha256" to hash,
                "clientUpdatedAt" to clientUpdatedAt,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        // Premium may have been switched off while the chunks were travelling. Leave those
        // unpublished chunks as harmless garbage rather than advancing the visible cloud copy.
        if (!PremiumAccess.isEnabled(context)) return
        // Publishing the head last means readers only ever see a fully uploaded snapshot.
        head.set(
            mapOf(
                "name" to library.name,
                "fileName" to library.fileName,
                "activeSnapshotId" to snapshotId,
                "schemaVersion" to CloudSnapshotCodec.SCHEMA_VERSION,
                "sha256" to hash,
                "cardCount" to (collection.cards?.sumOf { it?.quantityCount ?: 0 } ?: 0),
                "clientUpdatedAt" to clientUpdatedAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        database().collection("users").document(user.uid).set(
            mapOf(
                "displayName" to user.displayName,
                "email" to user.email,
                "lastSeenAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        rememberSnapshot(context, user.uid, library.id, hash, snapshotId)
        recordCloudBackup(context, user.uid, clientUpdatedAt)
        recordLastSync(context, user.uid)
        if (previousSnapshotId.isNotBlank() && previousSnapshotId != snapshotId) {
            runCatching { deleteSnapshot(head, previousSnapshotId) }.onFailure(::logFailure)
        }
    }

    private suspend fun download(head: DocumentReference): Biblio {
        val headData = head.get().await()
        val snapshotId = headData.getString("activeSnapshotId").orEmpty()
        require(snapshotId.isNotBlank()) { "La copia de ${head.id} está incompleta." }
        val snapshot = head.collection("snapshots").document(snapshotId)
        val metadata = snapshot.get().await()
        val count = metadata.getLong("chunkCount")?.toInt() ?: 0
        require(count > 0) { "La copia de ${head.id} no contiene datos." }
        val chunkDocuments = snapshot.collection("chunks").get().await().documents.associateBy { it.id }
        val chunks = (0 until count).map { index ->
            val encoded = chunkDocuments[index.toString().padStart(5, '0')]?.getString("data")
                ?: throw IllegalStateException("Falta una parte de la copia de ${head.id}.")
            Base64.decode(encoded, Base64.DEFAULT)
        }
        val bytes = CloudSnapshotCodec.join(chunks)
        val expectedHash = metadata.getString("sha256").orEmpty()
        require(expectedHash.isBlank() || CloudSnapshotCodec.sha256(bytes) == expectedHash) {
            "La copia de ${head.id} no superó la verificación de integridad."
        }
        return withContext(Dispatchers.Default) { CloudSnapshotCodec.decode(bytes) }
    }

    private fun saveRestored(context: Context, library: LibraryInfo, source: Biblio) {
        val restored = Biblio(library.fileName, library.name)
        source.cards.orEmpty().filterNotNull().forEach { restored.addCard(it.snapshotForPersistence()) }
        suppressUpload.incrementAndGet()
        try {
            DataUtils.saveSerializable(context, restored, library.fileName)
            if (LibraryCatalog.active(context).id == library.id) OcrCaptureActivity.mBiblio = restored
        } finally {
            suppressUpload.decrementAndGet()
        }
    }

    private suspend fun deleteSnapshot(head: DocumentReference, snapshotId: String) {
        val snapshot = head.collection("snapshots").document(snapshotId)
        snapshot.collection("chunks").get().await().documents
            .chunked(MAX_BATCH_OPERATIONS).forEach { group ->
                val batch = database().batch()
                group.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        snapshot.delete().await()
    }

    private fun cancelPendingUploads(uid: String) {
        pendingUploads.keys.filter { it.startsWith("$uid:") }.forEach { key ->
            pendingUploads.remove(key)?.cancel()
        }
    }

    private fun libraries(user: FirebaseUser) = database().collection("users")
        .document(user.uid).collection("libraries")

    private fun database(): FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun requireUser(context: Context): FirebaseUser {
        require(firebaseApp(context) != null) { "Falta app/google-services.json. Configura Firebase para usar la nube." }
        return FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Inicia sesión con Google para usar la nube.")
    }

    private fun firebaseApp(context: Context): FirebaseApp? = runCatching {
        FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    }.getOrNull()

    private fun rememberSnapshot(context: Context, uid: String, libraryId: String, hash: String, snapshot: String) {
        preferences(context).edit()
            .putString(key(uid, libraryId, KEY_HASH), hash)
            .putString(key(uid, libraryId, KEY_SNAPSHOT), snapshot)
            .apply()
    }

    private fun state(context: Context, uid: String, libraryId: String, prefix: String): String =
        preferences(context).getString(key(uid, libraryId, prefix), "").orEmpty()

    private fun key(uid: String, libraryId: String, prefix: String) = "$prefix$uid:$libraryId"

    private fun sameContents(first: Biblio, second: Biblio): Boolean =
        CloudSnapshotCodec.sha256(CloudSnapshotCodec.encode(first)) ==
            CloudSnapshotCodec.sha256(CloudSnapshotCodec.encode(second))

    private fun recordLastSync(context: Context, uid: String): Long = System.currentTimeMillis().also { now ->
        preferences(context).edit().putLong(KEY_LAST_SYNC + uid, now).apply()
    }

    private fun recordCloudBackup(context: Context, uid: String, value: Long) {
        preferences(context).edit().putLong(KEY_LAST_BACKUP + uid, value).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun logFailure(error: Throwable) {
        Log.e(TAG, "No se pudo sincronizar la Biblio", error)
    }
}
