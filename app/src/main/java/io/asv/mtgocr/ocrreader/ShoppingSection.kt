package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/** Embedded home destination; only an individual list is opened over it. */
class ShoppingSection(private val activity: AppCompatActivity, private val root: LinearLayout, private val openPhotos: Runnable) {
    private var generation=0
    init { CloudBuylistObserver(activity) { error ->
        if(root.visibility==View.VISIBLE) {
            if(error!=null)Toast.makeText(activity,error,Toast.LENGTH_LONG).show()
            reload()
        }
    } }
    private val import=activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && ShoppingAccess.require(activity))BuylistStore.worker.execute {
            val result=runCatching {
                val data=activity.contentResolver.openInputStream(uri)?.use { input ->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) { val n=input.read(buffer);if(n<0)break;require(out.size()+n<=8*1024*1024);out.write(buffer,0,n) };out.toByteArray()
                } ?: error("Input unavailable")
                BuylistStore.importList(activity,BuylistStore.decode(org.json.JSONObject(data.toString(Charsets.UTF_8))))
            }
            activity.runOnUiThread { if(!activity.isDestroyed && !activity.isFinishing) {
                result.onFailure { Toast.makeText(activity,R.string.buy_error,Toast.LENGTH_LONG).show() };reload()
            } }
        }
    }
    fun reload() {
        val request=++generation
        root.removeAllViews()
        val header=CollectionPopupChrome.panel(activity)
        header.addView(CollectionPopupChrome.label(activity,activity.getString(R.string.buy_title),24f))
        root.addView(header)
        header.addView(CollectionPopupChrome.button(activity,R.string.nav_photos) { openPhotos.run() }.apply { id=R.id.btnShoppingPhotos })
        if(!PremiumAccess.isEnabled(activity)) {
            header.addView(CollectionPopupChrome.label(activity,activity.getString(R.string.buy_premium_note)))
            return
        }
        val actions=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
        header.addView(actions)
        actions.addView(CollectionPopupChrome.button(activity,R.string.shopping_create_short) { BuylistActions.create(activity) }.apply { id=R.id.btnShoppingCreate;layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
        actions.addView(CollectionPopupChrome.button(activity,R.string.shopping_import_short) { import.launch(arrayOf("application/json","text/plain","application/octet-stream")) }.apply { layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
        val secondary=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL };header.addView(secondary)
        secondary.addView(CollectionPopupChrome.button(activity,R.string.buy_sync_web) { activity.startActivity(Intent(activity,CloudAccountActivity::class.java)) }.apply { layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
        secondary.addView(CollectionPopupChrome.icon(activity,android.R.drawable.ic_menu_search,R.string.buy_search) { activity.startActivity(Intent(activity,CardSearchActivity::class.java)) })
        header.addView(CollectionPopupChrome.label(activity,activity.getString(R.string.buy_sync_note),12f))
        val content=LinearLayout(activity).apply { id=R.id.buylistList;orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(activity).apply { addView(content) },LinearLayout.LayoutParams(-1,0,1f))
        val loading=CollectionPopupChrome.label(activity,activity.getString(R.string.price_details_loading));content.addView(loading)
        BuylistStore.worker.execute {
            val result=runCatching { BuylistStore.read(activity) }
            activity.runOnUiThread {
                if(request!=generation || activity.isFinishing || activity.isDestroyed || !PremiumAccess.isEnabled(activity))return@runOnUiThread
                content.removeAllViews()
                result.fold({ lists ->
                    if(lists.isEmpty())content.addView(CollectionPopupChrome.panel(activity).apply {
                        addView(CollectionPopupChrome.label(activity,activity.getString(R.string.buy_empty)))
                    })
                    lists.forEach { list ->
                        val panel=CollectionPopupChrome.panel(activity).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
                        val image=ImageView(activity).apply { scaleType=ImageView.ScaleType.CENTER_CROP;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                        panel.addView(image,LinearLayout.LayoutParams(CollectionPopupChrome.dp(activity,64),CollectionPopupChrome.dp(activity,90)))
                        CardImageCache.display(activity,list.entries.firstOrNull()?.image,image)
                        val text=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(CollectionPopupChrome.dp(activity,12),0,0,0) }
                        text.addView(CollectionPopupChrome.label(activity,list.name,20f))
                        val snapshot=list.history.lastOrNull()?.takeIf { it.composition==BuyPolicy.composition(list.entries) }
                        val value=snapshot?.let { String.format(Locale.getDefault(),"%.2f €",it.total)+(if(it.missing>0)" + ?" else "") } ?: "—"
                        text.addView(CollectionPopupChrome.label(activity,activity.getString(R.string.buy_list_summary,list.entries.sumOf { it.quantity },value),14f))
                        panel.addView(text,LinearLayout.LayoutParams(0,-2,1f))
                        panel.isClickable=true;panel.isFocusable=true
                        panel.setOnClickListener { if(ShoppingAccess.require(activity))activity.startActivity(Intent(activity,BuylistActivity::class.java).putExtra("listId",list.id)) }
                        panel.setOnLongClickListener {
                            if(ShoppingAccess.require(activity))android.app.AlertDialog.Builder(activity).setMessage(R.string.buy_confirm_delete)
                                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok) { _,_ ->
                                    BuylistStore.worker.execute { val deleted=runCatching { BuylistStore.remove(activity,list.id) };activity.runOnUiThread {
                                        if(!activity.isDestroyed && !activity.isFinishing) { if(deleted.isFailure)Toast.makeText(activity,R.string.buy_error,Toast.LENGTH_LONG).show();reload() }
                                    } }
                                }.show()
                            true
                        }
                        content.addView(panel,LinearLayout.LayoutParams(-1,-2).apply { topMargin=CollectionPopupChrome.dp(activity,10) })
                    }
                },{ content.addView(CollectionPopupChrome.label(activity,activity.getString(R.string.buy_error))) })
            }
        }
    }
}
