package io.asv.mtgocr.ocrreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import io.asv.mtgocr.ocrreader.data.CardDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class CardFaceImages(val front:String,val back:String) {
    companion object {
        fun fromJson(json:JSONObject):CardFaceImages? {
            // Split/adventure cards can have card_faces but only one physical image.
            if(json.has("image_uris")) return null
            val faces=json.optJSONArray("card_faces") ?: return null
            val front=faces.optJSONObject(0)?.optJSONObject("image_uris")?.optString("normal").orEmpty()
            val back=faces.optJSONObject(1)?.optJSONObject("image_uris")?.optString("normal").orEmpty()
            return if(front.isNotBlank() && back.isNotBlank() && front!=back) CardFaceImages(front,back) else null
        }
        fun imageId(url:String):String? {
            val uri=runCatching { URI(url) }.getOrNull() ?: return null
            if(uri.host!="cards.scryfall.io") return null
            return TripleVisualReference.parse(uri.path.substringAfterLast('/').substringBeforeLast('.'))?.catalogId
        }
    }
}

/** Metadata only; never runs network or index work on the camera/main thread. */
internal object CardFaceImagesLoader {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val client=OkHttpClient.Builder().callTimeout(3,TimeUnit.SECONDS).build()
    private val cache=object:LinkedHashMap<String,CardFaceImages?>(128,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,CardFaceImages?>?)=size>128
    }
    fun load(context:Context,url:String,reference:String,callback:(CardFaceImages?)->Unit) {
        val app=context.applicationContext
        worker.execute {
            val result=runCatching {
                // The image UUID takes precedence: it may be a localized printing.
                val id=CardFaceImages.imageId(url)
                    ?: CardDatabase.get(app).cardDao().printingsByUuids(listOf(reference)).firstOrNull()?.scryfallId
                    ?: TripleVisualReference.parse(reference)?.catalogId
                    ?: return@runCatching null
                if(cache.containsKey(id)) return@runCatching cache[id]
                val file=File(app.cacheDir,"card-face-meta/$id.json")
                val shared=File(app.cacheDir,"triple-card-meta/$id.json")
                val stored=sequenceOf(file,shared).mapNotNull { f ->
                    runCatching { if(f.isFile) JSONObject(f.readText()) else null }.getOrNull()
                }.firstOrNull { it.optString("id")==id }
                val json=stored ?: client.newCall(Request.Builder().url("https://api.scryfall.com/cards/$id")
                    .header("User-Agent","MtgOcr-CardFaces/1.0").header("Accept","application/json").build()).execute().use { response ->
                    check(response.isSuccessful)
                    JSONObject(requireNotNull(response.body).string()).also { data ->
                        check(data.getString("id")==id)
                        file.parentFile?.mkdirs()
                        val atomic=android.util.AtomicFile(file);val output=atomic.startWrite()
                        try { output.write(data.toString().toByteArray(Charsets.UTF_8));atomic.finishWrite(output) }
                        catch(e:Exception) { atomic.failWrite(output);throw e }
                    }
                }
                CardFaceImages.fromJson(json).also { cache[id]=it }
            }.getOrNull()
            main.post { callback(result) }
        }
    }
}

/** Presentation state only: changing face never updates the collection or scan target. */
internal class CardFaceToggle(private val button:ImageButton,private val display:(String)->Unit) {
    private var key=""
    private var generation=0
    private var faces:CardFaceImages?=null
    var displayedUrl=""; private set
    private var back=false
    init {
        button.setImageResource(R.drawable.ic_card_flip)
        button.visibility=View.GONE
        button.setOnClickListener {
            faces?.let { pair ->
                back=!back;displayedUrl=if(back) pair.back else pair.front
                describe();display(displayedUrl)
            }
        }
    }
    fun bind(url:String,reference:String) {
        val next="$reference|$url"
        if(key==next) return
        key=next;val token=++generation;faces=null;back=false;displayedUrl=url
        button.visibility=View.GONE;display(url)
        if(url.isBlank() && reference.isBlank()) return
        CardFaceImagesLoader.load(button.context,url,reference) { pair ->
            if(token!=generation) return@load
            faces=pair;back=pair!=null && (url==pair.back || url.contains("/back/"))
            describe();button.visibility=if(pair==null) View.GONE else View.VISIBLE
        }
    }
    private fun describe() { button.contentDescription=button.context.getString(if(back) R.string.card_show_front else R.string.card_show_back) }
    fun close() { generation++;button.setOnClickListener(null) }
}
