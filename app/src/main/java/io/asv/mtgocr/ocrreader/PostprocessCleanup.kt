package io.asv.mtgocr.ocrreader

import android.content.Context
import androidx.work.*
import io.asv.collectorvision.EdScanJobs
import io.asv.mtgocr.ocrreader.model.Biblio
import java.io.File
import java.util.concurrent.TimeUnit

/** Deletes only capture evidence, never inventory, catalogue images or recovery journals. */
object PostprocessCleanup {
    private const val TAG = "postprocess-cleanup"
    private val keyPattern = Regex("[0-9a-f-]{36}")

    @JvmStatic fun schedule(context: Context, library: String) = enqueue(context,library,false)
    @JvmStatic fun scheduleNow(context: Context, library: String) = enqueue(context,library,true)
    private fun enqueue(context:Context,library:String,immediate:Boolean) {
        // Never turn a successful collection write into a failure if scheduling is unavailable.
        runCatching {
            val request=OneTimeWorkRequestBuilder<PostprocessCleanupWorker>()
                .setInputData(workDataOf("library" to library)).addTag(TAG)
                .setInitialDelay(if(immediate) 0 else 30,TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("$TAG-$library",if(immediate) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,request)
        }.onFailure { android.util.Log.w(TAG,"Could not schedule evidence cleanup",it) }
    }

    internal fun eligible(owned: Boolean, states: List<String>): Boolean =
        !owned || (states.isNotEmpty() && states.all { it == "reviewed" })

    /** Exact UUID family, with canonical-path protection; no recursive directory deletion. */
    internal fun family(directory: File, key: String): List<File> {
        if (!keyPattern.matches(key)) return emptyList()
        val root=directory.canonicalFile
        return directory.listFiles().orEmpty().filter {
            it.isFile && it.name.startsWith("$key.") && it.canonicalFile.parentFile == root
        }
    }

    internal fun plan(context:Context,library:String):Set<String> = mutableSetOf<String>().also {
        clean(context,library,true,it)
    }

    /** Explicit opt-out, never called by automatic cleanup. Inventory is not rewritten. */
    internal fun discardPending(context:Context,library:String,report:StorageData.Report,progress:()->Unit) {
        if(StorageData.busy(context) || LibraryCatalog.activeFile(context)!=library) { report.blocked=true;return }
        val refs=StorageData.references(context) ?: run { report.blocked=true;return }
        clean(context,library,discard=true,guard={
            val valid=!StorageData.busy(context) && refs.stamps.all { (file,stamp) -> stamp==(file.lastModified() to file.length()) }
            if(!valid) report.blocked=true
            valid
        },protectedPaths=refs.paths,onProtected={ report.blocked=true }) { bytes ->
            if(bytes<0) report.failed++ else { report.bytes+=bytes;report.files++ }
            progress()
        }
    }

    internal fun clean(context: Context, library: String, dryRun:Boolean=false, planned:MutableSet<String>?=null, discard:Boolean=false, guard:()->Boolean={true}, protectedPaths:Set<String> = emptySet(), onProtected:()->Unit={}, onDeleted:(Long)->Unit = {}): Int {
        if (LibraryCatalog.activeFile(context) != library) return 0
        val libraryFile=File(context.filesDir,library)
        // Capture one persisted generation, never treat missing/corrupt inventory as empty.
        val snapshot=synchronized(DataUtils::class.java) {
            // The normal reader falls back to .bak; an older generation is not proof of deletion.
            val collection=runCatching {
                java.io.ObjectInputStream(libraryFile.inputStream().buffered()).use { it.readObject() as Biblio }
            }.getOrNull() ?: return 0
            Triple(collection,libraryFile.lastModified(),libraryFile.length())
        }
        val collection=snapshot.first
        val ids=collection.cards.map { it.collectionItemId }.toSet()
        val dir=EdScanJobs.directory(context)
        var directoryStamp=dir.lastModified()
        val initialFiles=dir.listFiles().orEmpty()
        val families=initialFiles.groupBy { it.name.substringBefore('.') }
        if(dir.lastModified()!=directoryStamp) throw java.util.ConcurrentModificationException()
        val versions=initialFiles.associateWith { it.lastModified() to it.length() }
        val entries=ScanReviewStore.read(context,library,true).groupBy { it.photoKey }
        val markers=initialFiles.filter { it.name.endsWith(".link.json") || it.name.endsWith(".provisional.json") }
        val owners=markers.mapNotNull { file ->
            val json=ScanReviewStore.json(file) ?: return@mapNotNull null
            val key=if(file.name.endsWith(".link.json")) file.name.removeSuffix(".link.json")
                else json.optString("photoKey").ifBlank { file.name.removeSuffix(".provisional.json") }
            if (!keyPattern.matches(key)) return@mapNotNull null
            Triple(key,file,json)
        }.groupBy { it.first }
        var removed=0
        owners.forEach { (key,refs) ->
            // Any foreign/unknown owner protects a potentially shared photograph.
            if(refs.any { it.third.optString("library") != library || it.third.optString("collectionItemId").isBlank() }) {
                if(discard && refs.any { it.third.optString("library")==library }) onProtected()
                return@forEach
            }
            val states=entries[key].orEmpty().map { it.state }
            if(!discard && refs.any { !eligible(it.third.optString("collectionItemId") in ids,states) && ScanReviewStore.hasPhoto(context,key) }) return@forEach
            // Provisional markers can have a different scan UUID from their photograph.
            // Keep those families until their own reference has gone, rather than erase shared data.
            val files=(families[key].orEmpty().filter { it.isFile && it.canonicalFile.parentFile==dir.canonicalFile }+
                refs.map { it.second }).distinctBy { it.path }
            if(discard && files.any { it.canonicalPath in protectedPaths }) { onProtected();return@forEach }
            // Keep ownership markers until all heavy payloads have been removed.
            val markersSet=refs.map { it.second }.toSet()
            val payload=files.filter { it !in markersSet }
            if(files.any { it !in versions }) return@forEach
            val stamps=files.associateWith { versions.getValue(it) }
            if(dryRun) { files.forEach { planned?.add(it.canonicalPath);removed++ };return@forEach }
            synchronized(DataUtils::class.java) {
                // A concurrent inventory edit aborts this pass; its save schedules another pass.
                // Lock only one photo, not thousands of files, so scanning can keep saving.
                if(LibraryCatalog.activeFile(context)!=library || libraryFile.lastModified()!=snapshot.second ||
                    libraryFile.length()!=snapshot.third || dir.lastModified()!=directoryStamp)
                    throw java.util.ConcurrentModificationException()
                if(stamps.any { (f,stamp) -> !f.isFile || (f.lastModified() to f.length())!=stamp }) return@forEach
                if(!guard()) return removed
                var ok=true
                payload.forEach { val size=it.length(); if(it.delete()) { removed++;onDeleted(size) } else if(it.exists()) { ok=false;onDeleted(-1) } }
                if(ok) markersSet.forEach { val size=it.length();if(it.delete()) { removed++;onDeleted(size) } else if(it.exists()) onDeleted(-1) }
                directoryStamp=dir.lastModified()
            }
        }
        return removed
    }
}

class PostprocessCleanupWorker(context: Context, params: WorkerParameters): Worker(context,params) {
    override fun doWork(): Result {
        val library=inputData.getString("library") ?: return Result.failure()
        return try {
            // Includes OCR, visual reanalysis and reliable-save chains. Never cancel user work.
            val active=WorkManager.getInstance(applicationContext).getWorkInfos(
                WorkQuery.Builder.fromStates(listOf(WorkInfo.State.RUNNING,WorkInfo.State.ENQUEUED,WorkInfo.State.BLOCKED)).build()
            ).get()
            if(active.any { it.id != id && "postprocess-cleanup" !in it.tags }) return Result.retry()
            var deferred=false
            StorageOperations.automatic(applicationContext) { deleted ->
                PostprocessCleanup.clean(applicationContext,library,onDeleted=deleted)
                val pending=applicationContext.getSharedPreferences("storage_pending",0)
                if(pending.getBoolean("images",false)) {
                    val report=StorageData.Report();var sent=0L;var sentFiles=0
                    val progress:()->Unit={
                        if(report.files>sentFiles) { deleted(report.bytes-sent);sent=report.bytes;sentFiles=report.files }
                    }
                    val targets=pending.getStringSet("targets",emptySet()).orEmpty()
                    StorageData.clean(applicationContext,0,report,progress,targets)
                    if(!report.blocked) StorageData.clean(applicationContext,1,report,progress,targets)
                    if(!report.blocked && report.failed==0) pending.edit().remove("images").remove("targets").commit() else deferred=true
                }
            }
            if(deferred) Result.retry() else Result.success()
        } catch(e: Exception) {
            android.util.Log.w("PostprocessCleanup","Evidence retained for retry",e)
            Result.retry()
        }
    }
}
