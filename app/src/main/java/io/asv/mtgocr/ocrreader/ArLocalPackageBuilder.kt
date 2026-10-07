package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.BitmapFactory
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Session
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Runs only on ArPackageStore.worker. Never opens/resumes the camera or writes collection data. */
object ArLocalPackageBuilder {
    data class Edition(val id:String,val name:String)
    private val client=OkHttpClient.Builder().callTimeout(45,TimeUnit.SECONDS).build()
    private fun bytes(url:String,check:()->Unit):ByteArray {
        check();require(url.startsWith("https://"));Thread.sleep(150)
        client.newCall(Request.Builder().url(url).header("User-Agent","MtgOcr-ArPackages/1.0").header("Accept","application/json,image/*").build()).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            val output=ByteArrayOutputStream()
            requireNotNull(response.body).byteStream().use { input ->
                val buffer=ByteArray(32768)
                while(true) { check();val n=input.read(buffer);if(n<0) break;require(output.size()+n<=8*1024*1024);output.write(buffer,0,n) }
            }
            return output.toByteArray()
        }
    }
    fun editions(c:Context):List<Edition> {
        val file=File(c.filesDir,"ar-set-list.json")
        val data=runCatching { bytes("https://api.scryfall.com/sets") {} }.getOrElse { if(file.isFile) file.readBytes() else throw it }
        val list=JSONObject(data.toString(Charsets.UTF_8)).getJSONArray("data")
        val today=java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(java.util.Date())
        val result=(0 until list.length()).map { list.getJSONObject(it) }.filter {
            !it.optBoolean("digital") && it.optString("released_at","9999")<=today && it.optString("set_type") !in setOf("token","memorabilia","minigame")
        }.map { Edition(it.getString("code").uppercase(Locale.US),it.getString("name")) }.filter { it.id.matches(Regex("[A-Z0-9]{2,8}")) }
        val temp=File(file.path+".part");temp.writeBytes(data);check(temp.renameTo(file))
        return result
    }
    fun generate(c:Context,edition:Edition,check:()->Unit,progress:(Long,Long)->Unit) {
        require(edition.id.matches(Regex("[A-Z0-9]{2,8}")))
        check();check(PremiumAccess.isEnabled(c));require(c.filesDir.usableSpace>40L*1024*1024)
        val config=JSONObject(c.assets.open("ar-editions.json").bufferedReader().use { it.readText() }).optJSONObject(edition.id)
        val sources=config?.getJSONArray("sources") ?: JSONArray().put(JSONObject().put("set",edition.id))
        val refs=JSONArray();val identities=hashSetOf<String>()
        for(i in 0 until sources.length()) {
            val source=sources.getJSONObject(i);val code=source.getString("set")
            val numbers=source.optJSONArray("numbers")?.let { a->(0 until a.length()).map { a.getString(it) }.toSet() }
            val found=hashSetOf<String>()
            var url:String?="https://api.scryfall.com/cards/search?q=set%3A${code}%20game%3Apaper&unique=prints&order=set"
            while(url!=null) {
                require(url.startsWith("https://api.scryfall.com/"))
                val page=JSONObject(bytes(url,check).toString(Charsets.UTF_8));val cards=page.getJSONArray("data")
                for(j in 0 until cards.length()) {
                    val card=cards.getJSONObject(j);val number=card.getString("collector_number")
                    if(numbers!=null && number !in numbers) continue
                    found.add(number)
                    val faces=if(card.has("image_uris")) listOf(card) else card.getJSONArray("card_faces").let { a->(0 until a.length()).map { a.getJSONObject(it) }.filter { it.has("image_uris") } }
                    require(faces.isNotEmpty())
                    val finishes=card.getJSONArray("finishes");val finish=if((0 until finishes.length()).any { finishes.getString(it)=="nonfoil" }) "nonfoil" else finishes.getString(0)
                    faces.forEachIndexed { index,face ->
                        val id=card.getString("id");val printing=id+if(index==0) "" else "_back"
                        require(identities.add(printing));require(refs.length()<1000) { c.getString(R.string.ar_generation_limit) }
                        refs.put(JSONObject().put("printing",printing).put("scryfallId",id).put("setCode",code).put("collectorNumber",number)
                            .put("label","${face.getString("name")} · $code #$number").put("image",face.getJSONObject("image_uris").getString("normal")).put("finish",finish).put("rarity",card.optString("rarity")))
                    }
                }
                url=if(page.optBoolean("has_more")) page.getString("next_page") else null
            }
            require(numbers==null || found.containsAll(numbers))
        }
        require(refs.length() in 1..1000);check();progress(0,refs.length().toLong())
        val session=Session(c)
        val databaseBytes:ByteArray
        try {
            val database=AugmentedImageDatabase(session)
            for(i in 0 until refs.length()) {
                check();check(PremiumAccess.isEnabled(c))
                val ref=refs.getJSONObject(i);val image=bytes(ref.getString("image"),check)
                val bitmap=requireNotNull(BitmapFactory.decodeByteArray(image,0,image.size))
                try { database.addImage(ref.getString("printing"),bitmap,.063f) } finally { bitmap.recycle() }
                progress(i+1L,refs.length().toLong())
            }
            check();databaseBytes=ByteArrayOutputStream().also { database.serialize(it) }.toByteArray()
        } finally { session.close() }
        check()
        val version=ArPackageIntegrity.hash(databaseBytes).take(20)
        val metadata=JSONObject().put("schemaVersion",1).put("id",edition.id).put("version",version).put("widthMeters",.063).put("references",refs).toString().toByteArray(Charsets.UTF_8)
        val base="${edition.id.lowercase(Locale.US)}-$version"
        fun artifact(ext:String,data:ByteArray)=ArPackageStore.Artifact("$base.$ext",data.size.toLong(),ArPackageIntegrity.hash(data))
        val entry=ArPackageStore.Entry(edition.id,edition.name,version,config?.getString("coverage") ?: c.getString(R.string.ar_main_set_only),refs.length(),artifact("imgdb",databaseBytes),artifact("json",metadata))
        ArPackageStore.install(c,entry,{ a,_->check();if(a.file.endsWith(".imgdb")) databaseBytes else metadata }) { _,_->check() }
    }
}
