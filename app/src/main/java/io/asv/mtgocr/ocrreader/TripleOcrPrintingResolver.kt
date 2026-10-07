package io.asv.mtgocr.ocrreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Completeness is required: a partial Room catalog cannot prove there is only one set. */
class TripleOcrPrintingResolver(context:Context,private val log:(String)->Unit) {
    data class Match(val id:String,val name:String,val set:String,val number:String,val image:String,val rarity:String,val finish:String,val variants:Boolean,val printing:String)
    private val directory=File(context.cacheDir,"triple-ocr-printings")
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val client=OkHttpClient.Builder().callTimeout(3,TimeUnit.SECONDS).build()
    private var busy=false
    @Volatile private var closed=false
    @Volatile private var call:okhttp3.Call?=null
    fun resolve(name:String,callback:(Match?)->Unit):Boolean {
        if(closed || busy) return false
        busy=true
        worker.execute {
            val start=System.nanoTime();var source="disk"
            val result=runCatching {
                val key=java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
                val file=File(directory,"$key.json")
                val cached=if(file.exists() && System.currentTimeMillis()-file.lastModified()<86400000) runCatching { JSONObject(file.readText()) }.getOrNull() else null
                val json=cached ?: run {
                    source="network"
                    val q=java.net.URLEncoder.encode("!\"${name.replace("\"", "")}\" game:paper","UTF-8")
                    val request=Request.Builder().url("https://api.scryfall.com/cards/search?q=$q&unique=prints&include_extras=true").header("User-Agent","MtgOcr-Triple/1.0").header("Accept","application/json").build()
                    val active=client.newCall(request);call=active;if(closed) active.cancel()
                    active.execute().use { response ->
                        check(response.isSuccessful)
                        JSONObject(requireNotNull(response.body).string()).also { data ->
                            runCatching { directory.mkdirs();file.writeText(data.toString()) }
                        }
                    }
                }
                val data=json.getJSONArray("data")
                val cards=(0 until data.length()).map { data.getJSONObject(it) }
                // has_more is conservative: never treat a truncated response as complete.
                if(!TripleOcrPrintingPolicy.singleSet(cards.map { it.getString("set") },!json.optBoolean("has_more",true))) null
                else {
                    val card=cards.firstOrNull { !it.optBoolean("promo") && !it.optBoolean("variation") && !it.optBoolean("full_art") } ?: cards.first()
                    val faces=card.optJSONArray("card_faces")?.let { a->(0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
                    val faceIndex=faces.indexOfFirst { TripleScanPolicy.agrees(it.optString("name"),name) }.coerceAtLeast(0)
                    val face=if(card.has("image_uris")) card else faces.getOrNull(faceIndex)
                    val image=face?.optJSONObject("image_uris")?.optString("normal").orEmpty()
                    if(image.isBlank() || !(TripleScanPolicy.agrees(card.getString("name"),name) || faces.any { TripleScanPolicy.agrees(it.optString("name"),name) })) null else {
                        val finishes=card.getJSONArray("finishes").let { a->(0 until a.length()).map { a.getString(it) } }
                        Match(card.getString("id"),name,card.getString("set").uppercase(),card.getString("collector_number"),image,card.optString("rarity"),if("nonfoil" in finishes) "nonfoil" else finishes.first(),cards.size>1,card.getString("id")+if(faceIndex>0) "_back" else "")
                    }
                }
            }
            call=null
            log("triple.ocrPrinting source=$source ms=${(System.nanoTime()-start)/1000000} uniqueSet=${result.getOrNull()!=null} failed=${result.isFailure}")
            main.post { busy=false;if(!closed) callback(result.getOrNull()) }
        }
        return true
    }
    fun close() { closed=true;call?.cancel();worker.shutdown() }
}
