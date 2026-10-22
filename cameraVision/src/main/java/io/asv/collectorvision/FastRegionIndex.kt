package io.asv.collectorvision

import android.content.Context
import android.util.JsonReader
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Immutable reference metadata, shared only by the opt-in Fast pipeline. */
class FastRegionIndex(private val context:Context) {
    data class Row(val id:String,val identity:String,val set:String,val released:String,val full:Boolean)
    val rows:List<Row>
    val byId:Map<String,Row>
    val identities:Map<String,String>
    private val manifest:JSONObject?
    val available:Boolean get()=manifest!=null
    init {
        val bytes=context.assets.open("edfast-regions/rows.json").use { it.readBytes() }
        require(sha(bytes)=="26621e82ff471a383802dc4a201e4d0b86460a4c5928e8cf3d7a2f1d4ecb8980") { "Fast metadata integrity" }
        // Avoid an additional 110k JSONArray tree on a phone's bounded heap.
        rows=JsonReader(bytes.inputStream().reader(Charsets.UTF_8)).use { reader ->
            val result=ArrayList<Row>(110278)
            reader.beginArray()
            while(reader.hasNext()) {
                reader.beginArray()
                result.add(Row(reader.nextString(),reader.nextString(),reader.nextString(),reader.nextString(),reader.nextBoolean()))
                reader.endArray()
            }
            reader.endArray();result
        }
        require(rows.size==110278 && rows.map { it.id }.toSet().size==rows.size)
        byId=rows.associateBy { it.id };identities=rows.associate { it.id to it.identity }
        manifest=runCatching { JSONObject(context.assets.open("edfast-regions/manifest.json").bufferedReader().use { it.readText() }).also {
            require(it.getInt("version")==1 && it.getBoolean("complete") && it.getInt("rows")==rows.size)
            require(it.getString("modelSha256")==MODEL_SHA)
            require(it.getJSONObject("files").getJSONObject("rows.json").getString("sha256")==sha(bytes))
            val regions=it.getJSONObject("regions")
            for((name,roi) in FastRegion.entries.associate { it.key to it.roi }) {
                val actual=regions.getJSONArray(name);require(actual.length()==4)
                for(i in roi.indices) require(kotlin.math.abs(actual.getDouble(i)-roi[i])<.000001)
            }
        } }.getOrNull()
    }
    /** Worker-only: load one selected profile, never both copies on the camera heap. */
    fun vectors(region:FastRegion):ShortArray {
        require(region!=FastRegion.FULL)
        val name="${region.key}.f16.bin"
        val entry=requireNotNull(manifest) { "Complete region index unavailable" }.getJSONObject("files").getJSONObject(name)
        val bytes=context.assets.open("edfast-regions/$name").use { it.readBytes() }
        require(bytes.size==rows.size*256 && entry.getInt("bytes")==bytes.size && sha(bytes)==entry.getString("sha256")) { "Region index integrity" }
        val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also { buffer.get(it) }
    }
    companion object {
        const val MODEL_SHA="bd13d8d60383c69da04dce261f32e93fdaeaa8fd618fbc991e7385f71b3d45df"
        private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

enum class FastRegion(val key:String,val roi:DoubleArray) {
    FULL("full",doubleArrayOf(0.0,0.0,1.0,1.0)),
    UPPER("upper",doubleArrayOf(0.0,0.0,1.0,.58)),
    ART("art",doubleArrayOf(.08,.15,.92,.55))
}
