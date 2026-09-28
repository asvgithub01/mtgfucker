package io.asv.mtgocr.ocrreader

import android.content.Context
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.collectorvision.EdScanJobs
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.json.JSONObject

/** Exact canonical names only, never OCR/fuzzy names. No database/network calls on the camera thread. */
internal class EdScanIdentityResolver(context: Context) : AutoCloseable {
    private val app=context.applicationContext
    val identities=ConcurrentHashMap<String,String>()
    private val requested=ConcurrentHashMap.newKeySet<String>()
    private val io=Executors.newSingleThreadExecutor()
    private val queued=java.util.concurrent.atomic.AtomicInteger()
    private val client=okhttp3.OkHttpClient.Builder().connectTimeout(3,java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5,java.util.concurrent.TimeUnit.SECONDS).build()
    fun request(ids: List<String>) {
        ids.filter { it.matches(Regex("[a-fA-F0-9-]{36}")) }.forEach { id ->
            if (queued.get() >= 16 || !requested.add(id)) return@forEach
            queued.incrementAndGet()
            io.execute {
                try {
                runCatching {
                    val local=CardDatabase.get(app).cardDao().printingByScryfallId(id)?.name
                    val name=local ?: run {
                        val cached=runCatching { JSONObject(File(EdScanJobs.directory(app),"price-$id.json").readText()) }
                            .getOrNull()?.takeIf { it.optString("id").equals(id,true) }
                        val data=cached ?: client.newCall(okhttp3.Request.Builder().url("https://api.scryfall.com/cards/$id")
                            .header("User-Agent","EdScan/0.4").build()).execute().use { response ->
                            check(response.isSuccessful); JSONObject(requireNotNull(response.body).string())
                        }
                        check(data.getString("id").equals(id,true));data.getString("name")
                    }
                    if(name.isNotBlank()) identities[id]=name.trim().lowercase(Locale.ROOT)
                }
                // Bounded one lookup per ID/session, including failures. A later session can retry offline misses.
                try { Thread.sleep(120) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                } finally { queued.decrementAndGet() }
            }
        }
    }
    override fun close() { io.shutdownNow() }
}
