package io.asv.mtgocr.ocrreader

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.asv.mtgocr.ocrreader.data.CardRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BuylistActivity:AppCompatActivity() {
    private lateinit var root:LinearLayout
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private var listId:String?=null
    private var current:BuyList?=null
    private val selected=linkedSetOf<String>()
    private var exportPath=""
    private var refreshing=false
    private var renderedListId:String?=null
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null)work { check(exportPath.isNotBlank());contentResolver.openOutputStream(uri)?.use { output -> java.io.File(exportPath).inputStream().use { it.copyTo(output) } } ?: error("Output unavailable") }
    }
    private val exportMkm=registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if(uri!=null)work { check(exportPath.isNotBlank());contentResolver.openOutputStream(uri)?.use { output -> java.io.File(exportPath).inputStream().use { it.copyTo(output) } } ?: error("Output unavailable") }
    }
    private val import=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)work {
            val bytes=contentResolver.openInputStream(uri)?.use { it.readBytesLimited(8*1024*1024) } ?: error("Input unavailable")
            val list=BuylistStore.decode(org.json.JSONObject(bytes.toString(Charsets.UTF_8)))
            // Imported history remains intact; new local ID prevents overwriting an existing list.
            BuylistStore.importList(this,list)
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        MagicPalette.applyTheme(this);super.onCreate(savedInstanceState)
        listId=savedInstanceState?.getString("listId") ?: intent.getStringExtra("listId")
        exportPath=savedInstanceState?.getString("exportPath").orEmpty()
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),0) };setContentView(root)
        val top=LinearLayout(this)
        top.addView(Button(this).apply { setText(R.string.close);setOnClickListener { finish() } })
        top.addView(TextView(this).apply { setText(R.string.buy_title);textSize=23f;setPadding(dp(12),dp(8),0,0) });root.addView(top)
        status=TextView(this);root.addView(status)
        body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        CollectionScreenBackground.install(this);status.setTextColor(Color.LTGRAY);status.textSize=12f
        (top.getChildAt(1) as TextView).setTextColor(Color.WHITE)
    }
    override fun onResume() { super.onResume();reload() }
    override fun onSaveInstanceState(out:Bundle) { super.onSaveInstanceState(out);out.putString("listId",listId);out.putString("exportPath",exportPath) }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun money(v:Double)=String.format(Locale.getDefault(),"%.2f €",v)
    private fun work(action:()->Unit) {
        status.setText(R.string.price_details_loading)
        BuylistStore.worker.execute {
            val result=runCatching(action)
            runOnUiThread { if(!isFinishing && !isDestroyed) { result.onFailure { Toast.makeText(this,R.string.buy_error,Toast.LENGTH_LONG).show() };reload() } }
        }
    }
    private fun reload() {
        BuylistStore.worker.execute {
            val result=runCatching { BuylistStore.read(this) }
            runOnUiThread {
                if(isFinishing || isDestroyed)return@runOnUiThread
                result.onFailure { status.setText(R.string.buy_error) }.onSuccess { lists ->
                    current=lists.firstOrNull { it.id==listId };render(lists)
                }
            }
        }
    }
    private fun row(parent:LinearLayout):LinearLayout {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val scroll=HorizontalScrollView(this).apply { addView(row) };parent.addView(scroll);return row
    }
    private fun button(parent:LinearLayout,label:Int,action:()->Unit)=Button(this).apply {
        setText(label);isAllCaps=false;textSize=12f;setOnClickListener { action() };parent.addView(this)
    }
    private fun render(lists:List<BuyList>) {
        val scrollState=if(renderedListId==current?.id)(0 until body.childCount).map { body.getChildAt(it) }.filterIsInstance<RecyclerView>().firstOrNull()?.layoutManager?.onSaveInstanceState() else null
        renderedListId=current?.id
        body.removeAllViews();status.text=if(refreshing)getString(R.string.price_details_loading) else getString(R.string.buy_reference_note)
        val list=current
        if(list==null) {
            listId=null
            val actions=row(body)
            button(actions,R.string.buy_new) { BuylistActions.create(this) }
            button(actions,R.string.buy_import) { import.launch(arrayOf("application/json","text/plain","application/octet-stream")) }
            button(actions,R.string.buy_search) { startActivity(Intent(this,CardSearchActivity::class.java)) }
            val scroll=ScrollView(this);val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };scroll.addView(content);body.addView(scroll)
            if(lists.isEmpty())content.addView(TextView(this).apply { setText(R.string.buy_empty);setTextColor(Color.LTGRAY) })
            lists.forEach { l ->
                val snap=l.history.lastOrNull()?.takeIf { it.composition==BuyPolicy.composition(l.entries) }
                content.addView(Button(this).apply {
                    isAllCaps=false;text=l.name+" · "+l.entries.sumOf { it.quantity }+" · "+(snap?.let { money(it.total)+if(it.missing>0)" + ?" else "" } ?: "—")
                    setOnClickListener { listId=l.id;selected.clear();reload() }
                    setOnLongClickListener { confirm { work { BuylistStore.remove(this@BuylistActivity,l.id) } };true }
                })
            };return
        }
        selected.retainAll(list.entries.map { it.id }.toSet())
        val snap=list.history.lastOrNull()?.takeIf { it.composition==BuyPolicy.composition(list.entries) }
        val total=TextView(this).apply {
            setTextColor(Color.WHITE);textSize=20f;text=list.name+" · "+(snap?.let { money(it.total)+if(it.missing>0)" + ?" else "" } ?: "—")
            setPadding(0,dp(10),0,dp(10));setOnClickListener { history(list,null) }
        };body.addView(total)
        val actions=row(body)
        button(actions,R.string.buy_lists) { listId=null;selected.clear();reload() }
        button(actions,R.string.buy_refresh) { refresh() }.isEnabled=!refreshing
        button(actions,R.string.buy_export) {
            AlertDialog.Builder(this).setTitle(R.string.buy_export).setItems(arrayOf(getString(R.string.buy_export_json),getString(R.string.buy_export_mkm))) { _,which ->
                if(which==0)prepareExport("buylist-${list.id}.json",false) { BuylistStore.encode(list).toString(2) }
                else AlertDialog.Builder(this).setMessage(R.string.buy_mkm_warning).setPositiveButton(android.R.string.ok) { _,_->
                    val batches=BuyPolicy.wantsBatches(list.entries)
                    if(batches.isNotEmpty())AlertDialog.Builder(this).setItems(batches.indices.map { "${it+1} / ${batches.size}" }.toTypedArray()) { _,index ->
                        prepareExport("mkm-${list.id}-${index+1}.txt",true) { batches[index] }
                    }.show()
                }.setNegativeButton(android.R.string.cancel,null).show()
            }.show()
        }
        button(actions,R.string.price_history_title) { history(list,null) }
        val selectRow=row(body)
        button(selectRow,R.string.select_all) { selected.clear();selected.addAll(list.entries.map { it.id });render(lists) }
        button(selectRow,R.string.buy_clear) { selected.clear();render(lists) }
        button(selectRow,R.string.buy_delete_selected) {
            val ids=selected.toSet();if(ids.isNotEmpty())confirm {
                work { BuylistStore.update(this,list.id) { it.copy(entries=it.entries.filterNot { e -> e.id in ids }) };BuylistStore.snapshot(this,list.id) }
                selected.clear()
            }
        }.apply { text=getString(R.string.buy_delete_selected)+" (${selected.size})";isEnabled=selected.isNotEmpty() }
        val recycler=RecyclerView(this).apply { layoutManager=GridLayoutManager(this@BuylistActivity,2);adapter=Cards(list) }
        body.addView(recycler,LinearLayout.LayoutParams(-1,0,1f))
        scrollState?.let { recycler.layoutManager?.onRestoreInstanceState(it) }
    }
    private fun prepareExport(name:String,mkm:Boolean,text:()->String) {
        BuylistStore.worker.execute {
            val result=runCatching { java.io.File(cacheDir,"buylist-export-${UUID.randomUUID()}").apply { writeText(text()) } }
            runOnUiThread { if(!isFinishing && !isDestroyed)result.onSuccess { exportPath=it.absolutePath;if(mkm)exportMkm.launch(name) else export.launch(name) }
                .onFailure { Toast.makeText(this,R.string.buy_error,Toast.LENGTH_LONG).show() } }
        }
    }
    private fun refresh() {
        if(refreshing)return
        refreshing=true;reload()
        CardRepository.get(this).refreshPriceIndex { ok ->
            BuylistStore.worker.execute {
                val result=runCatching { check(ok) }
                runOnUiThread { refreshing=false;if(!isFinishing && !isDestroyed) { if(result.isFailure)Toast.makeText(this,R.string.buy_error,Toast.LENGTH_LONG).show();reload() } }
            }
        }
    }
    private fun confirm(action:()->Unit) { AlertDialog.Builder(this).setMessage(R.string.buy_confirm_delete).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok) { _,_->action() }.show() }
    private fun history(list:BuyList,id:String?) {
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12)) }
        if(list.history.isEmpty())content.addView(TextView(this).apply { setText(R.string.price_history_empty) })
        list.history.asReversed().forEachIndexed { index,s ->
            val previous=list.history.getOrNull(list.history.lastIndex-index-1)
            val value=if(id==null)s.total else s.quotes.firstOrNull { it.entryId==id }?.amount
            val prev=if(id==null)previous?.takeIf { BuyPolicy.comparable(it,s) }?.total
                else previous?.takeIf { it.composition==s.composition }?.quotes?.firstOrNull { it.entryId==id }?.amount
            val delta=if(value!=null && prev!=null && prev>0)(value-prev)/prev*100 else null
            val date=if(id==null)"" else s.quotes.firstOrNull { it.entryId==id }?.date.orEmpty()
            content.addView(TextView(this).apply {
                setPadding(0,dp(8),0,dp(8));text=DateFormat.getDateTimeInstance().format(Date(s.at))+"\n"+(value?.let(::money) ?: "—")+
                    (if(id==null && s.missing>0)" + ? (${s.missing})" else "")+" · MKM · "+date+
                    (delta?.let { String.format(Locale.getDefault()," · %s %+.2f%%",if(it<0)"↓" else if(it>0)"↑" else "→",it) } ?: "")
                if(delta!=null && delta!=0.0)setTextColor(if(delta>0)Color.rgb(40,185,105) else Color.rgb(230,70,70))
            })
        }
        AlertDialog.Builder(this).setTitle(R.string.price_history_title).setView(ScrollView(this).apply { addView(content) }).setPositiveButton(R.string.close,null).show()
    }
    private inner class Cards(val list:BuyList):RecyclerView.Adapter<Cards.Holder>() {
        inner class Holder(val box:LinearLayout):RecyclerView.ViewHolder(box) {
            val check=CheckBox(this@BuylistActivity);val image=ImageView(this@BuylistActivity);val label=TextView(this@BuylistActivity)
            init { box.orientation=LinearLayout.VERTICAL;box.setPadding(dp(4),dp(4),dp(4),dp(4));box.addView(check);box.addView(image,LinearLayout.LayoutParams(-1,dp(220)));box.addView(label) }
        }
        override fun onCreateViewHolder(parent:ViewGroup,type:Int)=Holder(LinearLayout(this@BuylistActivity).apply { layoutParams=RecyclerView.LayoutParams(-1,-2) })
        override fun getItemCount()=list.entries.size
        override fun onBindViewHolder(h:Holder,position:Int) {
            val e=list.entries[position];h.check.setTextColor(Color.WHITE);h.check.text="${e.quantity} × NM · ${e.finish}";h.check.isChecked=e.id in selected
            h.check.setOnClickListener { if(h.check.isChecked)selected+=e.id else selected-=e.id;reload() }
            CardImageCache.display(this@BuylistActivity,e.image,h.image)
            val last=list.history.lastOrNull()?.takeIf { it.composition==BuyPolicy.composition(list.entries) }
            val quote=last?.quotes?.firstOrNull { it.entryId==e.id }
            val previous=list.history.getOrNull(list.history.lastIndex-1)?.takeIf { last!=null && it.composition==last.composition }?.quotes?.firstOrNull { it.entryId==e.id }?.amount
            val delta=quote?.amount?.let { amount -> previous?.takeIf { it>0 }?.let { (amount-it)/it*100 } }
            h.label.text="${e.name}\n${e.set} #${e.number} · "+(quote?.amount?.let(::money) ?: "—")+" · MKM"+
                (delta?.let { String.format(Locale.getDefault(),"\n%s %+.2f%%",if(it<0)"↓" else if(it>0)"↑" else "→",it) } ?: "")
            h.label.setTextColor(if(delta==null || delta==0.0)Color.LTGRAY else if(delta>0)Color.rgb(40,185,105) else Color.rgb(230,70,70))
            h.label.setOnClickListener { history(list,e.id) }
            h.image.setOnClickListener { startActivity(Intent(this@BuylistActivity,Main2Activity::class.java).putExtra(Main2Activity.EXTRA_CARD_NAME,e.name).putExtra("browseOnly",true).putExtra("printingUuid",e.uuid).putExtra("finish",e.finish)) }
            h.image.setOnLongClickListener { if(!selected.add(e.id))selected.remove(e.id);reload();true }
        }
    }
}
private fun java.io.InputStream.readBytesLimited(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true) { val n=read(buffer);if(n<0)break;require(out.size()+n<=limit);out.write(buffer,0,n) };return out.toByteArray()
}
