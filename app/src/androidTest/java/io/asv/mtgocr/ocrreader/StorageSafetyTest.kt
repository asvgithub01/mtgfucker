package io.asv.mtgocr.ocrreader

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ObjectOutputStream
import java.util.UUID

class StorageSafetyTest {
    private fun fixture(block:(Context,File)->Unit) {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val id=UUID.randomUUID().toString()
        val root=File(base.cacheDir,"storage-test-$id").apply { mkdirs() }
        val ctx=object:ContextWrapper(base) {
            override fun getFilesDir()=File(root,"files").apply { mkdirs() }
            override fun getCacheDir()=File(root,"cache").apply { mkdirs() }
            override fun getNoBackupFilesDir()=File(root,"no_backup").apply { mkdirs() }
            override fun getDatabasePath(name:String)=File(File(root,"databases").apply { mkdirs() },name)
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("test-$id-$name",mode)
            override fun getApplicationContext():Context=this
        }
        val library=File(ctx.filesDir,LibraryCatalog.DEFAULT_FILE)
        save(library,Biblio(library.name,"Fixture"))
        try { block(ctx,root) } finally { root.deleteRecursively() }
    }
    private fun save(file:File,value:java.io.Serializable) { ObjectOutputStream(file.outputStream()).use { it.writeObject(value) } }
    @Test fun explicitDiscardRemovesPendingEvidenceButNeverInventoryOrRecovery()=fixture { ctx,_ ->
        val key=UUID.randomUUID().toString()
        val card=CardInfo("Pending","","","","1")
        val library=File(ctx.filesDir,LibraryCatalog.DEFAULT_FILE)
        save(library,Biblio(library.name,"Fixture").apply { cards.add(card) })
        val before=library.readBytes()
        val dir=File(ctx.filesDir,"edscan").apply { mkdirs() }
        File(dir,"$key.link.json").writeText(org.json.JSONObject().put("library",library.name).put("collectionItemId",card.collectionItemId).toString())
        File(dir,"$key.json").writeText("{}")
        val photo=File(dir,"$key.jpg")
        InstrumentationRegistry.getInstrumentation().context.assets.open("triple-card.jpg").use { input -> photo.outputStream().use { input.copyTo(it) } }
        val recovery=File(File(ctx.filesDir,"review_batches").apply { mkdirs() },"recovery.json").apply { writeText("{}") }
        assertTrue(PostprocessCleanup.plan(ctx,library.name).isEmpty())
        assertFalse(ScanReviewStore.read(ctx,library.name).isEmpty())
        val bytes=dir.listFiles()!!.sumOf { it.length() }
        val report=StorageData.Report();PostprocessCleanup.discardPending(ctx,library.name,report,{})
        assertFalse(report.blocked);assertEquals(bytes,report.bytes);assertFalse(photo.exists())
        assertTrue(ScanReviewStore.read(ctx,library.name).isEmpty());assertTrue(recovery.exists())
        assertArrayEquals(before,library.readBytes())
    }
    @Test fun explicitDiscardProtectsSharedAndForeignEvidence()=fixture { ctx,_ ->
        val key=UUID.randomUUID().toString()
        val card=CardInfo("Shared","","","","1")
        val library=File(ctx.filesDir,LibraryCatalog.DEFAULT_FILE)
        save(library,Biblio(library.name,"Fixture").apply { cards.add(card) })
        val dir=File(ctx.filesDir,"edscan").apply { mkdirs() }
        val photo=File(dir,"$key.jpg").apply { writeBytes(ByteArray(42)) }
        File(dir,"$key.link.json").writeText(org.json.JSONObject().put("library",library.name).put("collectionItemId",card.collectionItemId).toString())
        val foreign=File(dir,"${UUID.randomUUID()}.provisional.json").apply {
            writeText(org.json.JSONObject().put("photoKey",key).put("library","other.Json").put("collectionItemId","foreign").toString())
        }
        val report=StorageData.Report();PostprocessCleanup.discardPending(ctx,library.name,report,{})
        assertTrue(report.blocked);assertEquals(0L,report.bytes);assertTrue(photo.exists())
        foreign.delete()
        save(File(ctx.filesDir,"photoScans.bin"),arrayListOf(PhotoScanEntry(imagePath=photo.path,state=PhotoScanEntry.STATE_READY)))
        val shared=StorageData.Report();PostprocessCleanup.discardPending(ctx,library.name,shared,{})
        assertTrue(shared.blocked);assertEquals(0L,shared.bytes);assertTrue(photo.exists())
    }
    @Test fun explicitDiscardRefusesActiveWorkAndCorruptInventory()=fixture { ctx,_ ->
        val key=UUID.randomUUID().toString();val dir=File(ctx.filesDir,"edscan").apply { mkdirs() }
        val photo=File(dir,"$key.jpg").apply { writeBytes(ByteArray(20)) }
        val library=LibraryCatalog.DEFAULT_FILE
        File(dir,"$key.link.json").writeText(org.json.JSONObject().put("library",library).put("collectionItemId","gone").toString())
        save(File(ctx.filesDir,"photoScans.bin"),arrayListOf(PhotoScanEntry(imagePath="pending")))
        val busy=StorageData.Report();PostprocessCleanup.discardPending(ctx,library,busy,{})
        assertTrue(busy.blocked);assertTrue(photo.exists())
        File(ctx.filesDir,"photoScans.bin").delete();File(ctx.filesDir,library).writeText("corrupt")
        val corrupt=StorageData.Report();PostprocessCleanup.discardPending(ctx,library,corrupt,{})
        assertTrue(corrupt.blocked);assertTrue(photo.exists())
    }
    @Test fun sharedImagesSurviveAndUnreferencedBytesAreMeasured()=fixture { ctx,_ ->
        val library=File(ctx.filesDir,LibraryCatalog.DEFAULT_FILE)
        val image="https://cards.scryfall.io/normal/front/a/b/fixture.jpg"
        val owned=CardInfo("Fixture","","",image,"1")
        save(library,Biblio(library.name,"Fixture").apply { cards.add(owned) })
        val directory=File(ctx.filesDir,"card_images").apply { mkdirs() }
        val shared=File(directory,StorageData.hash(image)).apply { writeBytes(ByteArray(51)) }
        val art=File(directory,StorageData.hash(LaunchArtworkUrl.resolve(image)!!)).apply { writeBytes(ByteArray(52)) }
        val unused=File(directory,StorageData.hash("https://unused.example/card.jpg")).apply { writeBytes(ByteArray(123)) }
        val before=library.readBytes()
        val report=StorageData.Report();StorageData.clean(ctx,0,report,{})
        assertFalse(report.blocked);assertEquals(123L,report.bytes);assertEquals(1,report.files)
        assertTrue(shared.exists());assertTrue(art.exists());assertFalse(unused.exists());assertArrayEquals(before,library.readBytes())
        save(library,Biblio(library.name,"Fixture"))
        val second=StorageData.Report();StorageData.clean(ctx,0,second,{})
        assertEquals(103L,second.bytes);assertFalse(shared.exists());assertFalse(art.exists())
    }
    @Test fun corruptCollectionAndAnalyzingPhotosProtectFiles()=fixture { ctx,_ ->
        val image=File(File(ctx.filesDir,"card_images").apply { mkdirs() },StorageData.hash("fixture")).apply { writeBytes(ByteArray(33)) }
        File(ctx.filesDir,LibraryCatalog.DEFAULT_FILE).writeText("corrupt")
        val report=StorageData.Report();StorageData.clean(ctx,0,report,{})
        assertTrue(report.blocked);assertEquals(0L,report.bytes);assertTrue(image.exists())
        save(File(ctx.filesDir,LibraryCatalog.DEFAULT_FILE),Biblio(LibraryCatalog.DEFAULT_FILE,"Fixture"))
        save(File(ctx.filesDir,"photoScans.bin"),arrayListOf(PhotoScanEntry(imagePath="pending")))
        val active=StorageData.Report();StorageData.clean(ctx,0,active,{})
        assertTrue(active.blocked);assertTrue(image.exists())
    }
    @Test fun absentPhotoLeavesCollectionButRemovesReviewQueueAndOrphanEvidence()=fixture { ctx,_ ->
        val key=UUID.randomUUID().toString()
        val card=CardInfo("Fixture","","","","1")
        val library=File(ctx.filesDir,LibraryCatalog.DEFAULT_FILE)
        save(library,Biblio(library.name,"Fixture").apply { cards.add(card) })
        val before=library.readBytes()
        val dir=File(ctx.filesDir,"edscan").apply { mkdirs() }
        File(dir,"$key.link.json").writeText(org.json.JSONObject().put("library",library.name).put("collectionItemId",card.collectionItemId).toString())
        File(dir,"$key.json").writeText("{\"source\":\"edscan\"}")
        assertTrue(ScanReviewStore.read(ctx,library.name).isEmpty())
        assertFalse(ScanReviewStore.read(ctx,library.name,true).isEmpty())
        val planned=PostprocessCleanup.plan(ctx,library.name)
        assertEquals(2,planned.size);assertTrue(File(dir,"$key.link.json").exists())
        val report=StorageData.Report();StorageData.clean(ctx,1,report,{})
        assertFalse(report.blocked);assertTrue(report.bytes>0);assertFalse(File(dir,"$key.link.json").exists())
        assertArrayEquals(before,library.readBytes())
    }
    @Test fun sharedPhotoIsNotDeletedWithOnlyOneEntry()=fixture { ctx,_ ->
        val photo=File(File(ctx.filesDir,"photo_scans").apply { mkdirs() },"shared.image").apply { writeBytes(ByteArray(25)) }
        val a=PhotoScanEntry(imagePath=photo.path,state=PhotoScanEntry.STATE_READY)
        val b=PhotoScanEntry(imagePath=photo.path,state=PhotoScanEntry.STATE_READY)
        save(File(ctx.filesDir,"photoScans.bin"),arrayListOf(a,b))
        val first=StorageData.Report();PhotoScanStore.deleteMeasured(ctx,a,first)
        assertEquals(0L,first.bytes);assertTrue(photo.exists());assertEquals(1,PhotoScanStore.entries(ctx).size)
        val last=StorageData.Report();PhotoScanStore.deleteMeasured(ctx,b,last)
        assertEquals(25L,last.bytes);assertFalse(photo.exists())
    }
    @Test fun anotherLibraryKeepsSharedImageEvenWhenNotActive()=fixture { ctx,_ ->
        val image="https://shared.example/card.jpg"
        val other="biblio_other.Json"
        ctx.getSharedPreferences("library_catalog",0).edit().putStringSet("libraries",setOf("other\tOther\t$other")).commit()
        save(File(ctx.filesDir,other),Biblio(other,"Other").apply { cards.add(CardInfo("Shared","","",image,"1")) })
        val file=File(File(ctx.filesDir,"card_images").apply { mkdirs() },StorageData.hash(image)).apply { writeBytes(ByteArray(70)) }
        val report=StorageData.Report();StorageData.clean(ctx,0,report,{})
        assertFalse(report.blocked);assertTrue(file.exists());assertEquals(0L,report.bytes)
    }
    @Test fun queuedWorkerProtectsCleanupWithoutCancellingIt()=fixture { ctx,_ ->
        val work=androidx.work.WorkManager.getInstance(ctx)
        val request=androidx.work.OneTimeWorkRequestBuilder<PostprocessCleanupWorker>()
            .setInitialDelay(1,java.util.concurrent.TimeUnit.DAYS).addTag("storage-protection-test").build()
        work.enqueue(request).result.get()
        try {
            val file=File(File(ctx.filesDir,"card_images").apply { mkdirs() },StorageData.hash("pending")).apply { writeBytes(ByteArray(20)) }
            val report=StorageData.Report();StorageData.clean(ctx,0,report,{})
            assertTrue(report.blocked);assertTrue(file.exists())
            assertEquals(androidx.work.WorkInfo.State.ENQUEUED,work.getWorkInfoById(request.id).get().state)
        } finally { work.cancelWorkById(request.id).result.get() }
    }
    @Test fun removingArPackageKeepsSharedArtifactsUntilLastOwner()=fixture { ctx,_ ->
        val directory=File(ctx.filesDir,"ar-packages").apply { mkdirs() }
        val sha="a".repeat(64)
        val db=File(directory,"$sha-shared.imgdb").apply { writeBytes(ByteArray(11)) }
        val meta=File(directory,"$sha-shared.json").apply { writeBytes(ByteArray(13)) }
        fun json(id:String)=org.json.JSONObject().put("id",id).put("name",id).put("version","b".repeat(20))
            .put("coverage","fixture").put("referenceCount",1)
            .put("database",org.json.JSONObject().put("file","shared.imgdb").put("bytes",11).put("sha256",sha))
            .put("metadata",org.json.JSONObject().put("file","shared.json").put("bytes",13).put("sha256",sha)).toString()
        ctx.getSharedPreferences("ar_packages",0).edit().putString("AA",json("AA")).putString("BB",json("BB")).commit()
        val first=StorageData.Report();ArPackageStore.removeDownloaded(ctx,"AA",first)
        assertEquals(0L,first.bytes);assertTrue(db.exists());assertTrue(meta.exists())
        val last=StorageData.Report();ArPackageStore.removeDownloaded(ctx,"BB",last)
        assertEquals(24L,last.bytes);assertFalse(db.exists());assertFalse(meta.exists())
    }
    @Test fun reliableCasesAlreadyAttemptedAreNotOfferedUntilEvidenceChanges()=fixture { ctx,_ ->
        val key=UUID.randomUUID().toString();val attempt=UUID.randomUUID().toString()
        val card=CardInfo("Fixture","","","","1").apply { printingUuid=UUID.randomUUID().toString();finish="nonfoil";languageCode="en" }
        val library=LibraryCatalog.DEFAULT_FILE
        save(File(ctx.filesDir,library),Biblio(library,"Fixture").apply { cards.add(card) })
        val dir=File(ctx.filesDir,"edscan").apply { mkdirs() }
        File(dir,"$key.link.json").writeText(org.json.JSONObject().put("library",library).put("collectionItemId",card.collectionItemId).toString())
        File(dir,"$key.jpg").writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("triple-card.jpg").use { it.readBytes() })
        File(dir,"$key.json").writeText("{\"source\":\"edscan\"}")
        File(dir,"$key.visual-request.json").writeText(org.json.JSONObject().put("attempt",attempt).put("both",true).toString())
        File(dir,"$key.$attempt.ocr.json").writeText("{\"readings\":[]}")
        val title=File(dir,"$key.$attempt.title.json").apply { writeText(org.json.JSONObject().put("printingUuid",card.printingUuid).put("state","consistent").toString()) }
        val at=System.currentTimeMillis()
        dir.listFiles()!!.forEach { it.setLastModified(at-2000) }
        val journal=File(ctx.filesDir,"review_batches/fixture").apply { mkdirs() }
        File(journal,"batch.json").writeText(org.json.JSONObject().put("library",library).put("createdAt",at).toString())
        File(journal,"$key.plan.json").writeText(org.json.JSONObject().put("fingerprint",ReliableReviewJournal.fingerprint(card)).toString())
        File(journal,"$key.result.json").writeText(org.json.JSONObject().put("rulesVersion",ReliableReviewPolicy.VERSION).put("status","skipped").put("reason","weak").put("at",at).toString())
        val entries=ScanReviewStore.read(ctx,library)
        assertFalse(entries.isEmpty())
        assertTrue(ReviewAnalysisBatch.actionable(ctx,library,entries).isEmpty())
        title.setLastModified(at+2000)
        assertEquals(setOf(key),ReviewAnalysisBatch.actionable(ctx,library,ScanReviewStore.read(ctx,library)))
    }
    @Test fun targetedDeletionDoesNotSweepUnrelatedCache()=fixture { ctx,_ ->
        val dir=File(ctx.filesDir,"card_images").apply { mkdirs() }
        val target=File(dir,StorageData.hash("target")).apply { writeBytes(ByteArray(13)) }
        val other=File(dir,StorageData.hash("other")).apply { writeBytes(ByteArray(17)) }
        val report=StorageData.Report();StorageData.clean(ctx,0,report,{},setOf(target.name))
        assertEquals(13L,report.bytes);assertFalse(target.exists());assertTrue(other.exists())
    }
    @Test fun corruptPhotoIsNotPresentedAsReviewable()=fixture { ctx,_ ->
        val key=UUID.randomUUID().toString()
        val dir=File(ctx.filesDir,"edscan").apply { mkdirs() }
        File(dir,"$key.jpg").writeText("not an image")
        assertFalse(ScanReviewStore.hasPhoto(ctx,key))
        File(dir,"$key.jpg").writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("triple-card.jpg").use { it.readBytes() })
        assertTrue(ScanReviewStore.hasPhoto(ctx,key))
    }
    @Test fun collectionDeletionReportsOnlyItsExclusiveFiles()=fixture { ctx,_ ->
        val url="https://deleted.example/image.jpg"
        val card=CardInfo("Deleted","","",url,"1")
        val dir=File(ctx.filesDir,"card_images").apply { mkdirs() }
        val removed=File(dir,StorageData.hash(url)).apply { writeBytes(ByteArray(32)) }
        val other=File(dir,StorageData.hash("https://other.example/image.jpg")).apply { writeBytes(ByteArray(18)) }
        StorageOperations.afterCollectionDelete(ctx,listOf(card))
        val done=java.util.concurrent.CountDownLatch(1)
        StorageOperations.worker.execute { done.countDown() }
        assertTrue(done.await(20,java.util.concurrent.TimeUnit.SECONDS))
        assertFalse(removed.exists());assertTrue(other.exists())
        val result=ctx.getSharedPreferences("storage_results",0)
        assertEquals(32L,result.getLong("bytes",-1));assertEquals(1,result.getInt("files",-1))
    }
    @Test fun managedInventoryIncludesModelsAndPreservesRoom()=fixture { ctx,_ ->
        val db=ctx.getDatabasePath("mtg_catalog.db").apply { writeBytes(ByteArray(42)) }
        val model=File(ctx.noBackupFilesDir,"model.onnx").apply { writeBytes(ByteArray(24)) }
        val snapshot=StorageData.snapshot(ctx)
        assertEquals(5,(0..4).map { snapshot.bytes(it) }.size)
        assertTrue(snapshot.items.any { it.file==db && !it.removable && it.group==2 })
        assertTrue(snapshot.items.any { it.file==model && !it.removable && it.group==3 })
    }
}
