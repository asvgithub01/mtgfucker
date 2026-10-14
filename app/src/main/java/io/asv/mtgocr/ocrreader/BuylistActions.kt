package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.EditText
import android.widget.Toast

object BuylistActions {
    @JvmStatic fun addCard(activity:Activity,card:io.asv.mtgocr.ocrreader.model.CardInfo) {
        if(card.printingUuid.isNullOrBlank() || runCatching { java.util.UUID.fromString(card.printingUuid) }.isFailure) { Toast.makeText(activity,R.string.buy_error,Toast.LENGTH_LONG).show();return }
        add(activity,listOf(BuyEntry(uuid=card.printingUuid,name=card.name.orEmpty(),set=card.setCode.orEmpty(),number=card.collectorNumber.orEmpty(),
            finish=io.asv.mtgocr.ocrreader.data.SelectedPrices.finish(card.finish),image=card.imgPath.orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),mcmId=card.mcmId.orEmpty(),language=card.languageCode.orEmpty(),setName=card.mcmSetName ?: card.setName.orEmpty())))
    }

    fun addExact(activity:Activity,name:String,set:String,number:String,finish:String) {
        io.asv.mtgocr.ocrreader.data.CardRepository.get(activity).loadCard(name) { options,error ->
            if(activity.isFinishing || activity.isDestroyed)return@loadCard
            val matches=options.filter { it.setCode.equals(set,true) && it.collectorNumber==number && it.finish==io.asv.mtgocr.ocrreader.data.SelectedPrices.finish(finish) }.distinctBy { it.printingUuid }
            if(error!=null || matches.size!=1)Toast.makeText(activity,R.string.buy_error,Toast.LENGTH_LONG).show()
            else add(activity,listOf(BuylistStore.entry(matches.single())))
        }
    }

    fun add(activity:Activity,entries:List<BuyEntry>) {
        if(entries.isEmpty()) { Toast.makeText(activity,R.string.buy_empty,Toast.LENGTH_SHORT).show();return }
        BuylistStore.worker.execute {
            val result=runCatching { BuylistStore.read(activity) }
            activity.runOnUiThread {
                if(activity.isFinishing || activity.isDestroyed)return@runOnUiThread
                result.onFailure { Toast.makeText(activity,R.string.buy_error,Toast.LENGTH_LONG).show() }.onSuccess { lists ->
                    AlertDialog.Builder(activity).setTitle(R.string.buy_add).setItems((listOf(activity.getString(R.string.buy_new))+lists.map { it.name }).toTypedArray()) { _,index ->
                        if(index==0) create(activity,entries) else save(activity) { BuylistStore.update(activity,lists[index-1].id) { it.copy(entries=BuyPolicy.add(it.entries,entries)) } }
                    }.show()
                }
            }
        }
    }
    fun create(activity:Activity,entries:List<BuyEntry> = emptyList()) {
        val input=EditText(activity).apply { hint=activity.getString(R.string.buy_name);maxLines=1 }
        AlertDialog.Builder(activity).setTitle(R.string.buy_new).setView(input).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.buy_create) { _,_ -> if(input.text.toString().trim().isNotEmpty())save(activity) { BuylistStore.create(activity,input.text.toString(),entries) } }.show()
    }
    private fun save(activity:Activity,operation:()->BuyList) {
        BuylistStore.worker.execute {
            val result=runCatching { val list=operation();BuylistStore.snapshot(activity,list.id) }
            activity.runOnUiThread {
                if(activity.isFinishing || activity.isDestroyed)return@runOnUiThread
                result.onSuccess { activity.startActivity(Intent(activity,BuylistActivity::class.java).putExtra("listId",it.id)) }
                    .onFailure { Toast.makeText(activity,R.string.buy_error,Toast.LENGTH_LONG).show() }
            }
        }
    }
}
