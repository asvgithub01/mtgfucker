package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import io.asv.mtgocr.ocrreader.data.CardRepository
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Main-thread owned, at most 20 images (384x536), two bitmap workers. No camera-thread I/O. */
class TripleArImageCache(context:Context,private val publish:(Map<String,TripleArOverlay.CardImage>,Set<String>)->Unit,private val log:(String)->Unit) {
    private val app=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newFixedThreadPool(2)
    private class Entry(val key:String) {
        var image:TripleArOverlay.CardImage?=null
        var loading=true
        var future:java.util.concurrent.Future<*>?=null
    }
    private val entries=linkedMapOf<String,Entry>()
    private var closed=false
    fun request(cards:List<ArPreloadCard>,languages:Map<String,String>) {
        if(closed) return
        val visible=cards.take(20);val keys=visible.map { it.printing }.toSet()
        entries.keys.filter { it !in keys }.forEach { remove(it) }
        visible.forEach { card ->
            val language=languages[card.printing].orEmpty()
            val key="${card.image}|${card.setCode}|${card.collectorNumber}|$language"
            if(entries[card.printing]?.key==key) return@forEach
            remove(card.printing)
            val entry=Entry(key);entries[card.printing]=entry;emit()
            if(language.isNotBlank() && card.setCode.isNotBlank() && card.collectorNumber.isNotBlank() && !card.printing.endsWith("_back")) {
                entry.future=CardRepository.get(app).loadImageLanguages(card.setCode,card.collectorNumber) { variants,_ ->
                    if(!current(card,entry)) return@loadImageLanguages
                    val localized=variants.firstOrNull { it.languageCode.equals(language,true) }
                    val caption=if(localized!=null) app.getString(R.string.triple_ar_image_language,language.uppercase()) else app.getString(R.string.triple_ar_image_reference)
                    load(card,entry,localized?.imageUrl ?: card.image,caption)
                }
            } else load(card,entry,card.image,app.getString(R.string.triple_ar_image_reference))
        }
        emit()
    }
    private fun current(card:ArPreloadCard,entry:Entry)=!closed && entries[card.printing]===entry
    private fun load(card:ArPreloadCard,entry:Entry,url:String,caption:String) {
        entry.future=worker.submit {
            val start=System.nanoTime()
            val request=Glide.with(app).load(url).asBitmap().into(384,536)
            val bitmap=try { request.get(4,TimeUnit.SECONDS).copy(Bitmap.Config.ARGB_8888,false) } catch(_:Exception) { null }
            finally { main.post { Glide.clear(request) } }
            main.post {
                if(!current(card,entry)) { bitmap?.recycle();return@post }
                entry.loading=false
                if(bitmap!=null) entry.image=TripleArOverlay.CardImage(bitmap,caption)
                log("triple.arImage ready=${bitmap!=null} ms=${(System.nanoTime()-start)/1000000}")
                emit()
            }
        }
    }
    private fun emit() { publish(entries.mapNotNull { (k,v)->v.image?.let { k to it } }.toMap(),entries.filterValues { it.loading }.keys.toSet()) }
    private fun remove(key:String) {
        val old=entries.remove(key) ?: return
        emit();old.future?.cancel(true);old.image?.bitmap?.recycle()
    }
    fun clear() { entries.keys.toList().forEach { remove(it) };emit() }
    fun close() { closed=true;clear();worker.shutdownNow() }
}
