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

/** Complete exact-printing catalog, NOT image variants or the partial local English catalog. */
internal class TriplePrintingLanguageResolver(context:Context, private val log:(String)->Unit) {
    private val directory=File(context.cacheDir,"triple-printing-languages-v1")
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val client=OkHttpClient.Builder().callTimeout(3,TimeUnit.SECONDS).build()
    private val cache=mutableMapOf<String,List<String>>()
    @Volatile private var closed=false
    @Volatile private var call:okhttp3.Call?=null
    fun resolve(set:String, number:String, callback:(String?)->Unit) {
        if(closed) return
        worker.execute {
            if(closed) return@execute
            val started=System.nanoTime()
            val key=set.lowercase()+":"+number
            var source="memory"
            val result=runCatching {
                val codes=cache[key] ?: run {
                    val hash=java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
                    val file=File(directory,"$hash.json")
                    source="disk"
                    val saved=if(file.exists() && System.currentTimeMillis()-file.lastModified()<86400000L) runCatching { JSONObject(file.readText()) }.getOrNull() else null
                    val json=saved ?: run {
                        source="network"
                        val q=java.net.URLEncoder.encode("set:${set.lowercase()} cn:\"${number.replace("\"", "")}\" lang:any game:paper","UTF-8")
                        val request=Request.Builder().url("https://api.scryfall.com/cards/search?q=$q&unique=prints&include_multilingual=true&include_extras=true")
                            .header("User-Agent","MtgOcr-Triple/1.0").header("Accept","application/json").build()
                        val active=client.newCall(request);call=active;if(closed) active.cancel()
                        active.execute().use { response ->
                            check(response.isSuccessful)
                            JSONObject(requireNotNull(response.body).string())
                        }
                    }
                    // Fail closed for pagination: a partial catalog must never prove EN-only.
                    check(!json.optBoolean("has_more",true))
                    val data=json.getJSONArray("data")
                    check(data.length()>0)
                    val found=(0 until data.length()).map { i ->
                        val card=data.getJSONObject(i)
                        check(card.getString("set").equals(set,true) && card.getString("collector_number")==number)
                        card.getString("lang").also { check(it.isNotBlank() && it!="und") }
                    }.distinct()
                    if(saved==null) runCatching { directory.mkdirs();file.writeText(json.toString()) }
                    cache[key]=found
                    found
                }
                TriplePrintingLanguagePolicy.single(codes,true)
            }
            call=null
            log("triple.printingLanguage set=$set number=$number source=$source ms=${(System.nanoTime()-started)/1000000} single=${result.getOrNull() ?: "und"} failed=${result.isFailure}")
            main.post { if(!closed) callback(result.getOrNull()) }
        }
    }
    fun close() { closed=true;call?.cancel();worker.shutdownNow() }
}
