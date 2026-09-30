package io.asv.mtgocr.ocrreader

import android.content.Context
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.io.File
import java.io.ObjectInputStream

/** Read-only history from durable journals, including batches created before this UI existed. */
object ReliableReviewHistory {
    data class Item(val photo: String, val name: String, val status: String, val reason: String, val changed: Boolean?)
    data class Batch(val id: String, val at: Long, val total: Int, val items: List<Item>) {
        val modified get()=items.count { it.status=="applied" && it.changed==true }
        val confirmed get()=items.count { it.status=="applied" && it.changed==false }
        val unknown get()=items.count { it.status=="applied" && it.changed==null }
        val manual get()=items.count { it.status=="skipped" }
        val errors get()=items.count { it.status=="error" }
        val missing get()=(total-items.size).coerceAtLeast(0)
    }
    fun changed(before: CardInfo, after: CardInfo): Boolean =
        before.name!=after.name || before.printingUuid!=after.printingUuid || before.languageCode!=after.languageCode ||
            before.finish!=after.finish || before.setCode!=after.setCode || before.collectorNumber!=after.collectorNumber

    fun read(context: Context, library: String): List<Batch> =
        File(context.filesDir,"review_batches").listFiles().orEmpty().mapNotNull { dir ->
            val meta=ScanReviewStore.json(File(dir,"batch.json")) ?: return@mapNotNull null
            if(meta.optString("library")!=library) return@mapNotNull null
            val items=dir.listFiles().orEmpty().filter { it.name.endsWith(".result.json") }.mapNotNull { file ->
                val result=ScanReviewStore.json(file) ?: return@mapNotNull null
                val key=file.name.removeSuffix(".result.json")
                val applied=result.optString("status")=="applied"
                val changed=if(!applied) null else runCatching {
                    fun card(suffix: String)=ObjectInputStream(File(dir,"$key.$suffix.ser").inputStream()).use { it.readObject() as CardInfo }
                    changed(card("before"),card("after"))
                }.getOrNull()
                Item(key,result.optString("name"),result.optString("status"),result.optString("reason"),changed)
            }
            Batch(dir.name,meta.optLong("createdAt"),meta.optInt("photos"),items)
        }.sortedByDescending { it.at }
}
