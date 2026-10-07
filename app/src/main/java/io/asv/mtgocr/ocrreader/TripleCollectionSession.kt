package io.asv.mtgocr.ocrreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.asv.mtgocr.ocrreader.data.*
import io.asv.mtgocr.ocrreader.model.CardInfo
import io.asv.collectorvision.EdScanJobs
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** One durable collection row per encounter. All mutations are serialized, never on the camera thread. */
internal class TripleCollectionSession(context:Context, private val log:(String)->Unit) {
    data class State(val event:Long,val target:TripleScanActivity.Target,val language:String?,val finish:String,
        val count:Int,val photo:ByteArray?,val manualLanguage:Boolean,val photoKey:String,
        val selectedOption:CardEditionOption?=null,val languageOrigin:String="",val manualEdition:Boolean=false)
    private val app=context.applicationContext
    private val library=LibraryCatalog.activeFile(app)
    private val group="Triple " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.ROOT).format(java.util.Date())
    private val main=Handler(Looper.getMainLooper())
    private val versions=ConcurrentHashMap<Long,Int>()
    private val saved=mutableMapOf<Long,CardInfo>() // confined to writer
    private val options=mutableMapOf<String,List<CardEditionOption>>() // main thread
    fun submit(state:State, callback:(CardInfo?,CardEditionOption?,Throwable?)->Unit) {
        val version=versions.merge(state.event,1) { a,b->a+b }!!
        fun apply(choices:List<CardEditionOption>,error:Throwable?) {
            if(versions[state.event]!=version) return
            val option=choices.firstOrNull { it.finish==state.finish }
            if(state.count>0 && option==null) { callback(null,null,error ?: IllegalStateException("Exact printing/finish unavailable"));return }
            writer.execute {
                val start=System.nanoTime()
                val result=runCatching {
                    if(versions[state.event]!=version) return@runCatching null
                    check(LibraryCatalog.activeFile(app)==library)
                    synchronized(LegacyCollectionStore) {
                        val previous=saved[state.event]
                        if(state.count==0) {
                            if(previous!=null) check(LegacyCollectionStore.editRecentQuantity(app,library,previous.collectionItemId,0,true,previous))
                            CardRepository.get(app).clearSelectedEdition(previous?.collectionItemId ?: "") {}
                            saved.remove(state.event)
                            return@synchronized null
                        }
                        val selected=requireNotNull(option).let { opt ->
                            if(state.target.source=="OCR") opt.copy(displayName=state.target.label.substringBefore(" · ")) else opt
                        }
                        var card=if(previous==null) LegacyCollectionStore.addCopy(app,selected,state.language.orEmpty(),
                            groupName=group,expectedLibraryFile=library,mergeExisting=false)
                        else requireNotNull(LegacyCollectionStore.applyReviewedPrinting(app,library,previous,selected,
                            state.language.orEmpty(),allowFinishChange=true)) { "Stored scan changed outside scanner" }
                        saved[state.event]=card.snapshotForPersistence()
                        if(card.quantityCount!=state.count) {
                            check(LegacyCollectionStore.editRecentQuantity(app,library,card.collectionItemId,state.count-card.quantityCount,false,card))
                            card=LegacyCollectionStore.cards(app).first { it.collectionItemId==card.collectionItemId }
                        }
                        card=requireNotNull(LegacyCollectionStore.markScanProvisional(app,library,card.collectionItemId,state.target.provisional))
                        saved[state.event]=card.snapshotForPersistence()
                        // The source describes evidence, not the UI engine label. Weak visual/OCR must be verified by art.
                        val source=if(state.target.source=="OCR" || state.target.provisional) "ocr" else "edscan"
                        val dir=EdScanJobs.directory(app)
                        state.photo?.let { jpeg ->
                            val file=File(dir,"${state.photoKey}.jpg")
                            if(!file.exists()) {
                                val atomic=android.util.AtomicFile(file);val out=atomic.startWrite()
                                try { out.write(jpeg);atomic.finishWrite(out) } catch(e:Exception) { atomic.failWrite(out);throw e }
                            }
                            ScanReviewStore.write(File(dir,"${state.photoKey}.json"),JSONObject()
                                .put("id",state.target.id).put("source",source).put("scanner","triple")
                                .put("finish",state.finish).put("createdAt",card.addedAt).put("languageStatus","pending")
                                .put("languageOrigin",state.languageOrigin).put("manualEdition",state.manualEdition)
                                .put("manualLanguage",state.manualLanguage).put("observedLanguage",state.language.orEmpty())
                                .put("provisional",state.target.provisional).put("event",state.event))
                            ScanReviewStore.write(File(dir,"${state.photoKey}.link.json"),JSONObject()
                                .put("library",library).put("collectionItemId",card.collectionItemId).put("printingUuid",selected.printingUuid))
                        }
                        val stored=LegacyCollectionStore.cards(app).first { it.collectionItemId==card.collectionItemId }
                        check(stored.printingUuid==selected.printingUuid && stored.finish==state.finish &&
                            stored.languageCode==state.language.orEmpty() && stored.quantityCount==state.count && stored.isProvisionalEdition==state.target.provisional)
                        saved[state.event]=stored.snapshotForPersistence()
                        val latch=java.util.concurrent.CountDownLatch(1)
                        var selectionError:Throwable?=null
                        CardRepository.get(app).selectReviewedEdition(card.collectionItemId,selected) { error -> selectionError=error;latch.countDown() }
                        check(latch.await(15,java.util.concurrent.TimeUnit.SECONDS)) { "Room selection timeout" }
                        selectionError?.let { throw it }
                        stored
                    }
                }
                log("triple.save event=${state.event} ms=${(System.nanoTime()-start)/1000000} ok=${result.isSuccess} photo=${state.photo!=null}")
                main.post { if(versions[state.event]==version) callback(result.getOrNull(),option,result.exceptionOrNull()) }
            }
        }
        if(state.count==0) { apply(emptyList(),null);return }
        state.selectedOption?.let { selected ->
            if(selected.finish==state.finish) { apply(listOf(selected),null);return }
            CardRepository.get(app).loadCachedPrinting(selected.cardName,selected.printingUuid,null,selected.setCode,selected.collectorNumber) { choices,error -> apply(choices,error) }
            return
        }
        options[state.target.id]?.let { apply(it,null);return }
        if(!state.target.id.matches(Regex("[a-fA-F0-9-]{36}"))) return // name-only is pending, never invent a UUID
        CardRepository.get(app).loadCollectorVisionPrinting(state.target.id) { choices,error ->
            if(choices.isNotEmpty()) options[state.target.id]=choices
            apply(choices,error)
        }
    }
    companion object { private val writer=Executors.newSingleThreadExecutor() }
}
