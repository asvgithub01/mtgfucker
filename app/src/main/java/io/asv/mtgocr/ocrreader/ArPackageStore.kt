package io.asv.mtgocr.ocrreader

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Static public artifacts. Premium is the app's existing local entitlement, not server authorization. */
object ArPackageStore {
    val worker=Executors.newSingleThreadExecutor()
    private val client=OkHttpClient.Builder().callTimeout(120,TimeUnit.SECONDS).followSslRedirects(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()
    data class Artifact(val file:String,val bytes:Long,val sha256:String)
    data class Entry(val id:String,val name:String,val version:String,val coverage:String,val count:Int,val database:Artifact,val metadata:Artifact)
    data class Installed(val entry:Entry,val database:File,val references:List<Reference>)
    data class Reference(val printing:String,val scryfallId:String,val label:String,val image:String,val finish:String,val rarity:String="")
    private fun root(c:Context)=File(c.filesDir,"ar-packages").apply { mkdirs() }
    private fun localFile(dir:File,a:Artifact)=File(dir,a.sha256+"-"+a.file)
    private fun prefs(c:Context)=c.getSharedPreferences("ar_packages",Context.MODE_PRIVATE)
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun artifact(j:JSONObject):Artifact {
        val file=j.getString("file"); val size=j.getLong("bytes"); val sha=j.getString("sha256")
        require(ArPackageIntegrity.safeFile(file) && size in 1..16L*1024*1024 && sha.matches(Regex("[0-9a-f]{64}")))
        return Artifact(file,size,sha)
    }
    fun parseEntry(j:JSONObject):Entry {
        val id=j.getString("id");val version=j.getString("version");val count=j.getInt("referenceCount")
        require(id.matches(Regex("[A-Z0-9]{2,8}")) && version.matches(Regex("[a-f0-9]{20}")) && count in 1..1000)
        return Entry(id,j.getString("name"),version,j.getString("coverage"),count,artifact(j.getJSONObject("database")),artifact(j.getJSONObject("metadata")))
    }
    private fun parseCatalog(bytes:ByteArray):List<Entry> {
        val j=JSONObject(bytes.toString(Charsets.UTF_8));require(j.getInt("schemaVersion")==1)
        val a=j.getJSONArray("packages");require(a.length()<=5000)
        return (0 until a.length()).map { parseEntry(a.getJSONObject(it)) }.also { require(it.map { e->e.id }.distinct().size==it.size) }
    }
    private fun readRemote(file:String,max:Long,progress:(Long)->Unit={}):ByteArray {
        val base=BuildConfig.AR_PACKAGES_URL
        require(base.startsWith("https://")) { "AR catalog URL not configured" }
        val request=Request.Builder().url(base.trimEnd('/')+"/"+file).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful); val body=requireNotNull(response.body)
            require(body.contentLength()<=max)
            body.byteStream().use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(32768);var total=0L
                while(true) { val n=input.read(buffer);if(n<0) break;total+=n;require(total<=max);output.write(buffer,0,n);progress(total) }
                return output.toByteArray()
            }
        }
    }
    private fun atomic(file:File,bytes:ByteArray) {
        val tmp=File(file.parentFile,file.name+".part");try { tmp.outputStream().use { it.write(bytes);it.fd.sync() };check(tmp.renameTo(file)) } finally { tmp.delete() }
    }
    fun catalog(c:Context,refresh:Boolean=true):List<Entry> {
        val local=File(root(c),"catalog.json")
        if(!refresh && local.exists()) return parseCatalog(local.readBytes())
        val bytes=readRemote("catalog.json",4L*1024*1024);val entries=parseCatalog(bytes);atomic(local,bytes);return entries
    }
    fun cachedCatalog(c:Context)=runCatching { parseCatalog(File(root(c),"catalog.json").readBytes()) }.getOrDefault(emptyList())
    private fun json(e:Entry)=JSONObject().put("id",e.id).put("name",e.name).put("version",e.version).put("coverage",e.coverage).put("referenceCount",e.count)
        .put("database",JSONObject().put("file",e.database.file).put("bytes",e.database.bytes).put("sha256",e.database.sha256))
        .put("metadata",JSONObject().put("file",e.metadata.file).put("bytes",e.metadata.bytes).put("sha256",e.metadata.sha256))
    fun referencesValid(c:Context)=prefs(c).all.values.all { runCatching { parseEntry(JSONObject(it as String)) }.isSuccess }
    fun installedEntries(c:Context)=prefs(c).all.values.mapNotNull { runCatching { parseEntry(JSONObject(it as String)) }.getOrNull() }
    fun installedVersion(c:Context,id:String)=prefs(c).getString(id,null)?.let { runCatching { parseEntry(JSONObject(it)).version }.getOrNull() }
    @Synchronized fun install(c:Context,e:Entry,source: ((Artifact,(Long)->Unit)->ByteArray)?=null,progress:(Long,Long)->Unit) {
        check(PremiumAccess.isEnabled(c))
        val directory=root(c);val total=e.database.bytes+e.metadata.bytes
        check(directory.usableSpace>total+2*1024*1024)
        var completed=0L
        for(a in listOf(e.database,e.metadata)) {
            check(PremiumAccess.isEnabled(c))
            val file=localFile(directory,a)
            val cached=file.takeIf { it.isFile && it.length()==a.bytes }?.readBytes()?.takeIf { hash(it)==a.sha256 }
            val report: (Long)->Unit = { progress(completed+it,total) }
            val bytes=cached ?: source?.invoke(a,report) ?: readRemote(a.file,a.bytes,report)
            ArPackageIntegrity.verify(bytes,a.bytes,a.sha256)
            if(cached==null) atomic(file,bytes)
            completed+=a.bytes;progress(completed,total)
        }
        // Validate both files before swapping the active version. Old package remains usable on failure.
        readInstalled(c,e)
        check(PremiumAccess.isEnabled(c));check(prefs(c).edit().putString(e.id,json(e).toString()).commit())
    }
    private fun readInstalled(c:Context,e:Entry):Installed {
        val dir=root(c);val db=localFile(dir,e.database);val metadata=localFile(dir,e.metadata)
        require(db.length()==e.database.bytes && metadata.length()==e.metadata.bytes)
        require(hash(db.readBytes())==e.database.sha256)
        val bytes=metadata.readBytes();require(hash(bytes)==e.metadata.sha256)
        val j=JSONObject(bytes.toString(Charsets.UTF_8));require(j.getInt("schemaVersion")==1 && j.getString("id")==e.id && j.getString("version")==e.version && j.getDouble("widthMeters")==0.063)
        val a=j.getJSONArray("references");require(a.length()==e.count)
        val refs=(0 until a.length()).map { index ->
            val r=a.getJSONObject(index); val image=r.getString("image");require(image.startsWith("https://"))
            Reference(r.getString("printing"),r.getString("scryfallId"),r.getString("label"),image,r.getString("finish"),r.optString("rarity"))
        };require(refs.map { it.printing }.distinct().size==refs.size)
        return Installed(e,db,refs)
    }
    @Synchronized fun removeDownloaded(c:Context,id:String,report:StorageData.Report) {
        if(StorageData.busy(c)) { report.blocked=true;return }
        if(!referencesValid(c)) { report.blocked=true;return }
        val entries=installedEntries(c)
        val entry=entries.firstOrNull { it.id==id } ?: return
        val shared=entries.filterNot { it.id==id }.flatMap { listOf(it.database,it.metadata) }.map { localFile(root(c),it).canonicalPath }.toSet()
        check(prefs(c).edit().remove(id).commit())
        listOf(entry.database,entry.metadata).forEach {
            val file=localFile(root(c),it)
            if(file.canonicalPath !in shared && StorageData.safeChild(root(c),file)) StorageData.deleteFile(file,report)
        }
    }
    fun installed(c:Context,id:String):Installed? = prefs(c).getString(id,null)?.let { readInstalled(c,parseEntry(JSONObject(it))) }
}
