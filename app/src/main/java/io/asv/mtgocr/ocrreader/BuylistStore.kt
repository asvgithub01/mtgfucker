package io.asv.mtgocr.ocrreader

import android.content.Context
import android.util.AtomicFile
import io.asv.mtgocr.ocrreader.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

data class BuyEntry(val id:String=UUID.randomUUID().toString(), val uuid:String, val name:String,
    val set:String, val number:String, val finish:String, val image:String, val mcmId:String,
    val quantity:Int=1, val language:String="", val condition:String="NM", val setName:String="")
data class BuyQuote(val entryId:String, val amount:Double?, val date:String)
data class BuySnapshot(val at:Long, val composition:String, val quotes:List<BuyQuote>) {
    val total get()=quotes.sumOf { it.amount ?: 0.0 }
    val missing get()=quotes.count { it.amount==null }
}
data class BuyList(val id:String=UUID.randomUUID().toString(), val name:String,
    val entries:List<BuyEntry> = emptyList(), val history:List<BuySnapshot> = emptyList())

object BuyPolicy {
    fun key(e:BuyEntry)="${e.uuid}|${e.finish}|${e.language}|${e.condition}"
    fun composition(entries:List<BuyEntry>)=entries.sortedBy { it.id }.joinToString(";") { "${it.id}:${key(it)}:${it.quantity}" }
    fun add(entries:List<BuyEntry>, added:List<BuyEntry>):List<BuyEntry> {
        val result=entries.toMutableList()
        added.forEach { e ->
            val index=result.indexOfFirst { key(it)==key(e) }
            if(index<0) result+=e else result[index]=result[index].copy(quantity=(result[index].quantity+e.quantity).coerceAtMost(999))
        }
        require(result.size<=10000)
        return result
    }
    fun wantsBatches(entries:List<BuyEntry>)=entries.chunked(150).map { batch -> batch.joinToString("\n") {
        "${it.quantity} ${it.name}" + if(it.setName.isBlank()) "" else " (${it.setName})"
    } }
    fun comparable(a:BuySnapshot,b:BuySnapshot)=a.composition==b.composition && a.missing==0 && b.missing==0
}

/** Independent from owned inventory. All disk work uses worker; failed reads never become empty writes. */
object BuylistStore {
    val worker=Executors.newSingleThreadExecutor()
    private fun file(c:Context)=AtomicFile(File(c.filesDir,"shopping-lists-v1.json"))
    fun read(c:Context):List<BuyList> {
        val f=file(c)
        if(!f.baseFile.exists() && !File(f.baseFile.path+".bak").exists()) return emptyList()
        val root=JSONObject(f.openRead().bufferedReader().use { it.readText() })
        require(root.getInt("version")==1)
        val rows=root.getJSONArray("lists")
        return (0 until rows.length()).map { decode(rows.getJSONObject(it)) }
    }
    private fun save(c:Context,lists:List<BuyList>) {
        val bytes=JSONObject().put("version",1).put("lists",JSONArray(lists.map(::encode))).toString().toByteArray()
        val f=file(c);val stream=f.startWrite()
        try { stream.write(bytes);f.finishWrite(stream) } catch(e:Exception) { f.failWrite(stream);throw e }
    }
    fun create(c:Context,name:String,entries:List<BuyEntry> = emptyList()):BuyList {
        require(name.trim().isNotEmpty());val lists=read(c)
        val list=BuyList(name=name.trim().take(120),entries=BuyPolicy.add(emptyList(),entries))
        save(c,lists+list);return list
    }
    fun update(c:Context,id:String,change:(BuyList)->BuyList):BuyList {
        val lists=read(c);val old=lists.first { it.id==id };val new=change(old)
        save(c,lists.map { if(it.id==id)new else it });return new
    }
    fun importList(c:Context,list:BuyList):BuyList {
        val copy=list.copy(id=UUID.randomUUID().toString());save(c,read(c)+copy);return copy
    }
    fun remove(c:Context,id:String) { save(c,read(c).filterNot { it.id==id }) }
    fun snapshot(c:Context,id:String):BuyList {
        val dao=CardDatabase.get(c).cardDao()
        return update(c,id) { list ->
            val rows=list.entries.map { it.uuid }.distinct().chunked(400).flatMap { dao.snapshotPricesFor(it) }
            val quotes=list.entries.map { e ->
                val row=rows.filter { it.printingUuid==e.uuid && SelectedPrices.finish(it.finish)==e.finish && it.provider=="cardmarket" && it.currency=="EUR" && it.amount.isFinite() && it.amount>=0 }.maxByOrNull { it.priceDate }
                BuyQuote(e.id,row?.amount?.times(e.quantity),row?.priceDate.orEmpty())
            }
            list.copy(history=list.history+BuySnapshot(System.currentTimeMillis(),BuyPolicy.composition(list.entries),quotes))
        }
    }
    fun entry(o:CardEditionOption)=BuyEntry(uuid=o.printingUuid,name=o.cardName,set=o.setCode,number=o.collectorNumber,
        finish=SelectedPrices.finish(o.finish),image=o.imageUrl.orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),mcmId=o.mcmId.orEmpty(),setName=o.mcmSetName ?: o.setName)
    fun entry(o:SetCardOption)=BuyEntry(uuid=o.printingUuid,name=o.cardName,set=o.setCode,number=o.collectorNumber,
        finish=SelectedPrices.finish(o.finish),image=o.imageUrl.orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),mcmId=o.mcmId.orEmpty(),setName=o.mcmSetName ?: o.setName)
    fun encode(l:BuyList):JSONObject=JSONObject().put("format","mtgfucker-buylist").put("version",1).put("id",l.id).put("name",l.name)
        .put("entries",JSONArray(l.entries.map { e -> JSONObject().put("id",e.id).put("uuid",e.uuid).put("name",e.name)
            .put("set",e.set).put("number",e.number).put("finish",e.finish).put("image",e.image).put("mcmId",e.mcmId)
            .put("setName",e.setName).put("quantity",e.quantity).put("language",e.language).put("condition",e.condition) }))
        .put("history",JSONArray(l.history.map { s -> JSONObject().put("at",s.at).put("composition",s.composition)
            .put("quotes",JSONArray(s.quotes.map { q -> JSONObject().put("entryId",q.entryId).put("amount",q.amount ?: JSONObject.NULL).put("date",q.date) })) }))
    fun decode(o:JSONObject):BuyList {
        require(o.getString("format")=="mtgfucker-buylist" && o.getInt("version")==1)
        val a=o.getJSONArray("entries");require(a.length()<=10000)
        val entries=(0 until a.length()).map { i -> val e=a.getJSONObject(i)
            val uuid=e.getString("uuid");UUID.fromString(uuid)
            val finish=e.getString("finish");require(finish in listOf("nonfoil","foil","etched"))
            val quantity=e.getInt("quantity");require(quantity in 1..999)
            val name=e.getString("name");require(name.isNotBlank() && name.length<=500 && !name.contains('\n') && !name.contains('\r'))
            require(e.optString("condition","NM")=="NM")
            val image=e.optString("image");require(image.isEmpty() || image.startsWith("https://"))
            val setName=e.optString("setName");require(setName.length<=300 && !setName.contains('\n') && !setName.contains('\r'))
            val mcm=e.optString("mcmId");require(mcm.isEmpty() || mcm.matches(Regex("[0-9]+")))
            BuyEntry(e.getString("id"),uuid,name,e.getString("set"),e.getString("number"),finish,image,mcm,quantity,e.optString("language"),setName=e.optString("setName"))
        }
        require(entries.map { it.id }.distinct().size==entries.size)
        require(entries.map(BuyPolicy::key).distinct().size==entries.size)
        val h=o.optJSONArray("history") ?: JSONArray();require(h.length()<=10000)
        val history=(0 until h.length()).map { i -> val s=h.getJSONObject(i);val qs=s.getJSONArray("quotes");require(qs.length()<=10000)
            BuySnapshot(s.getLong("at"),s.getString("composition"),(0 until qs.length()).map { j -> val q=qs.getJSONObject(j)
                val amount=if(q.isNull("amount"))null else q.getDouble("amount");require(amount==null || amount.isFinite() && amount>=0)
                BuyQuote(q.getString("entryId"),amount,q.optString("date")) }) }
        val name=o.getString("name");require(name.isNotBlank() && name.length<=120)
        return BuyList(o.getString("id"),name,entries,history)
    }
}
