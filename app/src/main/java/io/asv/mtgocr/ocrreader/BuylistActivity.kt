package io.asv.mtgocr.ocrreader

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.asv.mtgocr.ocrreader.data.CardRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class BuylistActivity:AppCompatActivity() {
    private lateinit var root:LinearLayout
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private var listId:String?=null
    private var current:BuyList?=null
    private val selected=linkedSetOf<String>()
    private lateinit var total:TextView
    private var refreshing=false
    private var renderedListId:String?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        CollectionPopupChrome.prepare(this,fullScreen=true);super.onCreate(savedInstanceState)
        if(!ShoppingAccess.require(this)) { finish();return }
        listId=savedInstanceState?.getString("listId") ?: intent.getStringExtra("listId")
        if(listId.isNullOrBlank()) { finish();return }
        root=CollectionPopupChrome.install(this,R.string.buy_title,fullScreen=true) { header ->
            status=CollectionPopupChrome.label(this,"",12f)
            header.addView(status,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10);bottomMargin=dp(10) })
            total=CollectionPopupChrome.label(this,"",20f).apply {
                id=R.id.buylistTotal;setPadding(dp(8),dp(8),dp(8),dp(8))
            }
            header.addView(total,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) })
        }
        body=LinearLayout(this).apply { id=R.id.buylistBody;orientation=LinearLayout.VERTICAL }
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        CloudBuylistObserver(this) { error ->
            if(error!=null)Toast.makeText(this,error,Toast.LENGTH_LONG).show()
            reload()
        }
    }
    override fun onResume() { super.onResume();if(!PremiumAccess.isEnabled(this)) { finish();return };if(::body.isInitialized)reload() }
    override fun onSaveInstanceState(out:Bundle) { super.onSaveInstanceState(out);out.putString("listId",listId) }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun money(v:Double)=String.format(Locale.getDefault(),"%.2f €",v)
    private fun work(action:()->Unit) {
        status.setText(R.string.price_details_loading)
        BuylistStore.worker.execute {
            val result=runCatching { check(PremiumAccess.isEnabled(this));action() }
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
        val panel=CollectionPopupChrome.panel(this)
        panel.addView(row);parent.addView(panel,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) });return row
    }
    private fun button(parent:LinearLayout,label:Int,action:()->Unit)=CollectionPopupChrome.button(this,label,action).apply {
        textSize=12f;parent.addView(this)
    }
    private fun render(lists:List<BuyList>) {
        val scrollState=if(renderedListId==current?.id)(0 until body.childCount).map { body.getChildAt(it) }.filterIsInstance<RecyclerView>().firstOrNull()?.layoutManager?.onSaveInstanceState() else null
        renderedListId=current?.id
        body.removeAllViews();status.text=if(refreshing)getString(R.string.price_details_loading) else getString(R.string.buy_reference_note)
        val list=current
        if(list==null) { finish();return }
        selected.retainAll(list.entries.map { it.id }.toSet())
        val snap=list.history.lastOrNull()?.takeIf { it.composition==BuyPolicy.composition(list.entries) }
        total.text=list.name+" · "+(snap?.let { money(it.total)+if(it.missing>0)" + ?" else "" } ?: "—")
        total.setOnClickListener { history(list,null) }
        val actions=row(body)
        button(actions,R.string.buy_refresh) { refresh() }.apply { isEnabled=!refreshing;layoutParams=LinearLayout.LayoutParams(-1,-2) }
        val selectRow=row(body)
        selectRow.gravity=android.view.Gravity.CENTER_VERTICAL
        selectRow.addView(CheckBox(this).apply {
            id=R.id.buylistSelectAll;setText(R.string.select_all);setTextColor(Color.WHITE);textSize=12f
            isChecked=list.entries.isNotEmpty() && selected.size==list.entries.size
            isEnabled=list.entries.isNotEmpty()
            setOnCheckedChangeListener { _,checked ->
                selected.clear();if(checked)selected.addAll(list.entries.map { it.id });render(lists)
            }
        },LinearLayout.LayoutParams(0,-2,1f))
        button(selectRow,R.string.buy_delete_selected) {
            val ids=selected.toSet();if(ids.isNotEmpty())confirm {
                work { BuylistStore.update(this,list.id) { it.copy(entries=it.entries.filterNot { e -> e.id in ids }) };BuylistStore.snapshot(this,list.id) }
                selected.clear()
            }
        }.apply { id=R.id.buylistDeleteSelected;text=getString(R.string.buy_delete_selected)+" (${selected.size})";isEnabled=selected.isNotEmpty();layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
        val recycler=RecyclerView(this).apply { layoutManager=GridLayoutManager(this@BuylistActivity,2);adapter=Cards(list) }
        body.addView(recycler,LinearLayout.LayoutParams(-1,0,1f))
        scrollState?.let { recycler.layoutManager?.onRestoreInstanceState(it) }
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
        override fun onCreateViewHolder(parent:ViewGroup,type:Int)=Holder(CollectionPopupChrome.panel(this@BuylistActivity).apply { layoutParams=RecyclerView.LayoutParams(-1,-2).apply { setMargins(dp(3),dp(5),dp(3),dp(5)) } })
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
