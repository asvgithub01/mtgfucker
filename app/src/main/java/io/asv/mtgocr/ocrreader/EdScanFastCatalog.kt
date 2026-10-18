package io.asv.mtgocr.ocrreader

import android.content.Context
import io.asv.collectorvision.Hit
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Worker-owned, bounded read-only cache. No network/indexing/collection mutation in camera lane. */
internal class EdScanFastCatalog(private val context:Context) {
    var referenceIndex:io.asv.collectorvision.FastRegionIndex?=null
    private data class Metadata(val identity:String,val set:String,val date:String,val prices:Map<String,Double>)
    private val cache=object:LinkedHashMap<String,Metadata?>(256,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Metadata?>?)=size>512
    }
    fun candidates(hits:List<Hit>,finish:String):List<EdScanFastIdentity.Candidate> {
        val missing=hits.map { it.cardId.removeSuffix("_back") }.filter { !cache.containsKey(it) }
        if(missing.isNotEmpty()) {
            val dao=CardDatabase.get(context).cardDao()
            val rows=dao.printingsByScryfallIds(missing).sortedWith(compareBy({ if(it.side=="a") 0 else if(it.side==null) 1 else 2 },{it.uuid}))
            val prices=dao.pricesFor(rows.map { it.uuid }).groupBy { it.printingUuid }
            for(id in missing) {
                val row=rows.firstOrNull { it.scryfallId==id }
                cache[id]=if(row!=null) Metadata(row.name.trim().lowercase(Locale.ROOT),row.setCode,row.releaseDate,
                    prices[row.uuid].orEmpty().filter { it.currency in listOf("EUR","USD") && it.amount.isFinite() && it.amount>=0 }
                        .associate { (if(it.finish=="normal") "nonfoil" else it.finish) to PriceCurrency.convert(context,it.amount,it.currency) })
                else runCatching {
                    val file=File(context.cacheDir,"triple-card-meta/$id.json")
                    val json=JSONObject(file.readText());require(json.getString("id")==id)
                    val pricesJson=json.optJSONObject("prices")
                    val quotes=listOf("nonfoil" to "", "foil" to "_foil", "etched" to "_etched").mapNotNull { (finish,suffix) ->
                        val eur=pricesJson?.optString("eur$suffix")?.toDoubleOrNull()
                        val usd=pricesJson?.optString("usd$suffix")?.toDoubleOrNull()
                        val amount=eur ?: usd
                        if(amount==null || !amount.isFinite() || amount<0) null
                        else finish to PriceCurrency.convert(context,amount,if(eur!=null) "EUR" else "USD")
                    }.toMap()
                    Metadata(json.getString("name").trim().lowercase(Locale.ROOT),json.getString("set"),json.optString("released_at"),quotes)
                }.getOrNull()
            }
        }
        return hits.map { hit ->
            val id=hit.cardId.removeSuffix("_back")
            val m=cache[id];val reference=referenceIndex?.byId?.get(hit.cardId)
            EdScanFastIdentity.Candidate(id,hit.score,reference?.identity ?: m?.identity,
                reference?.set ?: m?.set.orEmpty(),reference?.released ?: m?.date.orEmpty(),m?.prices?.get(finish)) }
    }
    // Retry a missing identity after the existing asynchronous display lookup warms its disk cache.
    fun retryUnknown() { if(referenceIndex==null) cache.entries.removeAll { it.value==null } }
}
