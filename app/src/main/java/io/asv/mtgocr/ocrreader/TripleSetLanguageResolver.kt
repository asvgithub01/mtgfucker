package io.asv.mtgocr.ocrreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One cached SetList request, not one network query per language/card. Missing data stays unknown. */
internal class TripleSetLanguageResolver(context:Context,private val log:(String)->Unit) {
    private val file=AtomicFile(File(context.cacheDir,"triple-set-languages.json"))
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val client=OkHttpClient.Builder().callTimeout(12,TimeUnit.SECONDS).build()
    private var memory:Map<String,List<String>>?=null
    @Volatile private var closed=false
    @Volatile private var call:okhttp3.Call?=null
    fun load(sets:Set<String>,callback:(List<String>?)->Unit) {
        if(closed) return
        worker.execute {
            if(closed) return@execute
            val started=System.nanoTime()
            var source="memory"
            val result=runCatching {
                if(sets.isEmpty()) TripleLanguageScope.allCodes else {
                    val catalog=memory ?: run {
                        source="disk"
                        val saved=runCatching { file.openRead().bufferedReader().use { it.readText() } }.getOrNull()
                        val fresh=saved!=null && System.currentTimeMillis()-file.baseFile.lastModified()<86400000L
                        val raw=if(fresh) saved!! else try {
                            source="network"
                            val active=client.newCall(Request.Builder().url("https://mtgjson.com/api/v5/SetList.json")
                                .header("User-Agent","MtgOcr-Triple/1.0").header("Accept","application/json").build())
                            call=active;if(closed) active.cancel()
                            active.execute().use { response-> check(response.isSuccessful);requireNotNull(response.body).string() }
                        } catch(error:Exception) { source="staleDisk";saved ?: throw error }
                        val data=JSONObject(raw).getJSONArray("data")
                        val parsed=buildMap<String,List<String>> {
                            for(i in 0 until data.length()) {
                                val set=data.getJSONObject(i);val langs=set.optJSONArray("languages") ?: continue
                                put(set.getString("code").uppercase(java.util.Locale.ROOT),(0 until langs.length()).map { langs.getString(it) })
                            }
                        }
                        check(parsed.isNotEmpty())
                        if(source=="network") runCatching {
                            val out=file.startWrite()
                            try { out.write(raw.toByteArray(Charsets.UTF_8));file.finishWrite(out) }
                            catch(error:Exception) { file.failWrite(out);throw error }
                        }
                        memory=parsed;parsed
                    }
                    TripleLanguageScope.available(sets,catalog)
                }
            }.getOrNull()
            call=null
            log("triple.setLanguages scope=${TripleLanguageScope.key(sets)} source=$source ms=${(System.nanoTime()-started)/1000000} count=${result?.size ?: 0}")
            main.post { if(!closed) callback(result) }
        }
    }
    fun close() { closed=true;call?.cancel();worker.shutdownNow() }
}
