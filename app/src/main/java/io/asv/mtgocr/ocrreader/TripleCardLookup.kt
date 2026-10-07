package io.asv.mtgocr.ocrreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.asv.collectorvision.EdScanPerf
import io.asv.mtgocr.ocrreader.data.CardDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Display-only, bounded lookup. Never synchronizes a set or invents an inventory UUID. */
class TripleCardLookup(context:Context,private val log:(String)->Unit = { EdScanPerf.event(it) }) {
    data class Result(val printing:String,val name:String,val set:String,val number:String,val image:String,val finish:String,val price:Pair<Double,String>?=null,val rarity:String="")
    private val app=context.applicationContext
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val busy=AtomicBoolean(false)
    private val client=OkHttpClient.Builder().callTimeout(2,TimeUnit.SECONDS).build()
    private val cache=object:LinkedHashMap<String,Result>(32,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Result>?)=size>64
    }
    @Volatile private var closed=false
    @Volatile private var call:okhttp3.Call?=null
    /** Main-thread API; false means a lookup is in flight, caller can retry on a later frame. */
    fun lookup(id:String,callback:(Result?)->Unit):Boolean {
        if(closed || !id.matches(Regex("[a-fA-F0-9-]{36}"))) return false
        cache[id]?.let { callback(it);return true }
        if(!busy.compareAndSet(false,true)) return false
        val started=System.nanoTime()
        worker.execute {
            var source="local"
            val result=runCatching {
                val dao=CardDatabase.get(app).cardDao()
                val local=dao.printingByScryfallId(id)
                if(local!=null) {
                    val finishes=local.finishes.split(',').filter { it.isNotBlank() }
                    val finish=if("nonfoil" in finishes) "nonfoil" else finishes.firstOrNull() ?: "nonfoil"
                    val price=dao.pricesFor(listOf(local.uuid)).firstOrNull { it.finish==finish || (finish=="nonfoil" && it.finish=="normal") }
                    Result(local.uuid,local.name,local.setCode,local.collectorNumber,local.imageUrl.orEmpty(),finish,price?.let { it.amount to it.currency },local.rarity)
                } else {
                    val file=File(app.cacheDir,"triple-card-meta/$id.json")
                    val cached=runCatching { if(file.isFile) JSONObject(file.readText()) else null }.getOrNull()
                    val json=if(cached!=null && cached.optString("id")==id) { source="disk";cached } else {
                        source="singleCardNetwork"
                        val request=Request.Builder().url("https://api.scryfall.com/cards/$id").header("User-Agent","MtgOcr-Triple/1.0").header("Accept","application/json").build()
                        val active=client.newCall(request);call=active
                        if(closed) active.cancel()
                        active.execute().use { response ->
                            check(response.isSuccessful)
                            val data=JSONObject(requireNotNull(response.body).string())
                            require(data.getString("id")==id)
                            file.parentFile?.mkdirs();val temp=File(file.path+".part")
                            try { temp.writeText(data.toString());check(temp.renameTo(file)) } finally { temp.delete() }
                            data
                        }
                    }
                    val images=json.optJSONObject("image_uris") ?: json.optJSONArray("card_faces")?.optJSONObject(0)?.optJSONObject("image_uris")
                    val finishes=json.getJSONArray("finishes").let { a->(0 until a.length()).map { a.getString(it) } }
                    // Scryfall ID is an AR reference key only, not a fabricated MTGJSON UUID.
                    Result(id,json.getString("name"),json.getString("set").uppercase(),json.getString("collector_number"),images?.optString("normal").orEmpty(),if("nonfoil" in finishes) "nonfoil" else finishes.first(),rarity=json.optString("rarity"))
                }
            }
            call=null
            log("triple.lookup source=$source ok=${result.isSuccess} ms=${(System.nanoTime()-started)/1000000} error=${result.exceptionOrNull()?.javaClass?.simpleName ?: "none"}")
            main.post {
                busy.set(false)
                if(!closed) { result.getOrNull()?.let { cache[id]=it };callback(result.getOrNull()) }
            }
        }
        return true
    }
    fun close() { closed=true;call?.cancel();worker.shutdown() }
}
