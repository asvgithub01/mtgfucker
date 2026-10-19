package io.asv.mtgocr.ocrreader

import android.content.Context
import androidx.work.*
import io.asv.mtgocr.ocrreader.model.Biblio
import java.io.File
import java.io.ObjectInputStream
import java.security.MessageDigest

/** Fail closed: unknown references, active work and shared databases are never deletion candidates. */
object StorageData {
    data class Report(var bytes:Long=0,var files:Int=0,var failed:Int=0,var blocked:Boolean=false)
    data class Item(val file:File,val group:Int,val removable:Boolean,val bytes:Long=file.length())
    data class Snapshot(val items:List<Item>,val busy:Boolean) {
        fun bytes(group:Int)=items.filter { it.group==group }.sumOf { it.bytes }
        fun reclaimable(group:Int)=items.filter { it.group==group && it.removable }.sumOf { it.bytes }
    }
    internal data class References(val images:Set<String>,val paths:Set<String>,val stamps:Map<File,Pair<Long,Long>>)
    internal fun hash(url:String)=MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }+".image"
    internal fun imageNames(url:String):Set<String> {
        if(url.isBlank())return emptySet()
        val clean=url.trim()
        val urls=mutableSetOf(clean)
        for(size in listOf("small","normal","large","png","border_crop","art_crop")) {
            urls+=clean.replace(Regex("/(small|normal|large|png|border_crop|art_crop)/"),"/$size/")
        }
        urls.toList().forEach { value ->
            urls+=value.replace("/front/","/back/");urls+=value.replace("/back/","/front/")
        }
        return urls.map(::hash).toSet()
    }
    fun busy(context:Context):Boolean = try {
        io.asv.collectorvision.EdScanInferencePriority.isBusy() ||
            WorkManager.getInstance(context).getWorkInfos(WorkQuery.Builder.fromStates(listOf(WorkInfo.State.RUNNING,WorkInfo.State.ENQUEUED,WorkInfo.State.BLOCKED)).build())
                .get().any { "postprocess-cleanup" !in it.tags } ||
            PhotoScanStore.entries(context).any { it.state==PhotoScanEntry.STATE_ANALYZING }
    } catch(_:Exception) { true }

    internal fun references(context:Context):References? = runCatching {
        val files=context.filesDir
        val images=mutableSetOf<String>();val paths=mutableSetOf<String>();val stamps=mutableMapOf<File,Pair<Long,Long>>()
        fun stamp(file:File) { stamps[file]=file.lastModified() to file.length() }
        val libraries=LibraryCatalog.libraries(context)
        for(lib in libraries) {
            val file=File(files,lib.fileName)
            // An absent default library in a new installation is not proof that old images are unowned.
            require(file.isFile)
            stamp(file)
            val collection=ObjectInputStream(file.inputStream().buffered()).use { it.readObject() as Biblio }
            collection.cards.forEach { card ->
                images+=imageNames(card.imgPath.orEmpty())
                card.scanPhotoPaths.filter { it.isNotBlank() }.forEach { paths+=File(files,it).canonicalPath }
            }
        }
        // Pending manual review can reference an alternative printing even without running workers.
        File(files,"edscan").listFiles().orEmpty().filter { it.name.endsWith(".match.json") }.forEach { file ->
            val match=ScanReviewStore.json(file) ?: error("Unreadable review reference")
            images+=imageNames(match.optString("imageUrl"))
        }
        val photoFile=File(files,"photoScans.bin");stamp(photoFile)
        if(photoFile.exists()) {
            val photos=ObjectInputStream(photoFile.inputStream().buffered()).use { it.readObject() as List<*> }
            for(value in photos) {
                val photo=value as PhotoScanEntry
                paths+=File(photo.imagePath).canonicalPath
                photo.cards.forEach { images+=imageNames(it.imageUrl) }
            }
        }
        require(ArPackageStore.referencesValid(context))
        for(entry in ArPackageStore.installedEntries(context)) {
            val installed=requireNotNull(ArPackageStore.installed(context,entry.id))
            installed.references.forEach { images+=imageNames(it.image) }
        }
        require(stamps.all { (file,version)->version==(file.lastModified() to file.length()) })
        References(images,paths,stamps)
    }.getOrNull()

    internal fun safeChild(root:File,file:File):Boolean = runCatching {
        val relative=file.absoluteFile.relativeTo(root.absoluteFile).path
        relative.isNotEmpty() && relative.split(File.separatorChar).none { it==".." || it=="." } &&
            file.canonicalPath.startsWith(root.canonicalPath+File.separator) &&
            File(root.canonicalFile,relative).absolutePath==file.canonicalPath
    }.getOrDefault(false)
    private fun files(root:File):List<File> = if(!root.isDirectory) emptyList() else root.walkTopDown()
        .onEnter { it==root || safeChild(root,it) }.filter { it.isFile && safeChild(root,it) }.toList()
    private fun group(relative:String):Int = when(relative.substringBefore('/')) {
        "card_images" -> 0
        "edscan","scan_evidence","review_batches" -> 1
        "ar-packages","collectorvision","collector-vision","art_hash_samples" -> 3
        "photo_scans" -> 4
        else -> if(relative.contains("ar-") || relative.contains("collectorvision") || relative.contains("onnx")) 3 else 2
    }
    fun snapshot(context:Context):Snapshot {
        val active=busy(context)
        val refs=if(active)null else references(context)
        val root=context.filesDir
        val postprocess=if(active) emptySet() else runCatching { PostprocessCleanup.plan(context,LibraryCatalog.activeFile(context)) }.getOrDefault(emptySet())
        val items=files(root).map { file ->
            val rel=file.relativeTo(root).invariantSeparatorsPath
            val section=group(rel)
            val removable=!active && (file.canonicalPath in postprocess || rel=="mtgjson/AllPricesToday.json.gz" || (refs!=null && when {
                rel.startsWith("card_images/") && file.extension=="image" -> file.name !in refs.images
                rel.startsWith("scan_evidence/") -> file.canonicalPath !in refs.paths
                else -> false
            }))
            Item(file,section,removable)
        }.toMutableList()
        // Shared Room database includes selections/inventory metadata: never remove it as a cache.
        items+=files(context.getDatabasePath("placeholder").parentFile!!).map { Item(it,2,false) }
        items+=files(context.noBackupFilesDir).map { Item(it,3,false) }
        items+=files(context.cacheDir).map { file ->
            val relative=file.relativeTo(context.cacheDir).invariantSeparatorsPath
            val group=when { relative.startsWith("mtgjson/")->2;relative.startsWith("ar-")->3;else->4 }
            // Only explicitly disposable render caches, older than a day; no arbitrary cache sweep.
            val safe=relative.startsWith("image_manager_disk_cache/") || relative.startsWith("card-edition-fingerprints/")
            Item(file,group,!active && safe && System.currentTimeMillis()-file.lastModified()>86_400_000)
        }
        return Snapshot(items,active)
    }
    internal fun deleteFile(file:File,report:Report) {
        if(!file.isFile)return
        val size=file.length()
        if(file.delete()) { report.bytes+=size;report.files++ } else report.failed++
    }
    fun clean(context:Context,group:Int,report:Report,progress:()->Unit,targets:Set<String>?=null) {
        if(busy(context)) { report.blocked=true;return }
        if(group==2) {
            io.asv.mtgocr.ocrreader.data.CardRepository.get(context).discardPriceDownload(report)
            progress();return
        }
        if(group==1) {
            PostprocessCleanup.clean(context,LibraryCatalog.activeFile(context)) { bytes ->
                if(bytes<0) report.failed++ else { report.bytes+=bytes;report.files++ };progress()
            }
        }
        val snapshot=snapshot(context)
        if(snapshot.busy) { report.blocked=true;return }
        val refs=references(context) ?: run { report.blocked=true;return }
        var checked=0
        val libraries=LibraryCatalog.libraries(context).map { it.fileName }.toSet()
        for(item in snapshot.items.filter { it.group==group && it.removable && (targets==null || it.file.name in targets || it.file.canonicalPath in targets) }) {
            if(checked++ % 32==0 && busy(context)) { report.blocked=true;return }
            synchronized(DataUtils::class.java) {
                if(LibraryCatalog.libraries(context).map { it.fileName }.toSet()!=libraries) { report.blocked=true;return }
                if(refs.stamps.any { (file,version)->version!=(file.lastModified() to file.length()) }) { report.blocked=true;return }
                if(!safeChild(context.filesDir,item.file) && !safeChild(context.cacheDir,item.file)) { report.failed++;return@synchronized }
                deleteFile(item.file,report)
            }
            progress()
        }
    }
}
