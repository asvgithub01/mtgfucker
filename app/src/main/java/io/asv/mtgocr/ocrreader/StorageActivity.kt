package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

class StorageActivity:AppCompatActivity() {
    private lateinit var root:LinearLayout
    private var loading=false
    private val titles=listOf(R.string.storage_images,R.string.storage_postprocess,R.string.storage_catalog,R.string.storage_scanner,R.string.storage_photos)
    override fun onCreate(savedInstanceState:Bundle?) {
        MagicPalette.applyTheme(this);super.onCreate(savedInstanceState)
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16) }
        setContentView(ScrollView(this).apply { addView(root) })
        CollectionScreenBackground.install(this)
        root.setBackgroundColor(0xB0000000.toInt())
    }
    override fun onResume() { super.onResume();load() }
    private fun label()=TextView(this).apply { setTextColor(android.graphics.Color.WHITE) }
    private fun button(text:String,action:()->Unit) = Button(this).apply { this.text=text;setOnClickListener { action() } }
    private fun load() {
        if(loading)return
        loading=true;root.removeAllViews()
        root.addView(label().apply { setText(R.string.storage_title);textSize=24f })
        root.addView(ProgressBar(this))
        StorageOperations.worker.execute {
            val result=runCatching { StorageData.snapshot(applicationContext) }
            runOnUiThread {
                loading=false
                if(isFinishing || isDestroyed)return@runOnUiThread
                root.removeViews(1,root.childCount-1)
                result.onSuccess { snapshot ->
                    root.addView(label().apply {
                        text=getString(R.string.storage_total,StorageOperations.mb(snapshot.items.sumOf { it.bytes }))+"\n"+getString(R.string.storage_protected)
                    })
                    if(snapshot.busy) root.addView(label().apply { setText(R.string.storage_busy) })
                    titles.forEachIndexed { group,title ->
                        val items=snapshot.items.filter { it.group==group }
                        root.addView(label().apply { text=getString(title);textSize=20f;setPadding(0,24,0,8) })
                        root.addView(label().apply { text=getString(R.string.storage_summary,StorageOperations.mb(snapshot.bytes(group)),items.size,StorageOperations.mb(snapshot.reclaimable(group))) })
                        root.addView(button(getString(R.string.storage_view)) {
                            val rows=items.sortedByDescending { it.bytes }
                            val list=ListView(this)
                            list.adapter=object:BaseAdapter() {
                                override fun getCount()=rows.size
                                override fun getItem(position:Int)=rows[position]
                                override fun getItemId(position:Int)=position.toLong()
                                override fun getView(position:Int,old:View?,parent:android.view.ViewGroup):View {
                                    val item=rows[position]
                                    val relative=item.file.absolutePath.removePrefix(applicationInfo.dataDir+"/")
                                    return (old as? TextView ?: TextView(this@StorageActivity).apply { setPadding(24,16,24,16) }).apply {
                                        text="${StorageOperations.mb(item.bytes)} · $relative"+if(item.removable) "" else " · "+getString(R.string.storage_kept)
                                    }
                                }
                            }
                            list.setOnItemClickListener { _,_,position,_ ->
                                val file=rows[position].file
                                if(group==0 || file.extension.lowercase() in setOf("jpg","jpeg","png","webp","image")) {
                                    startActivity(Intent(this,CardImageActivity::class.java)
                                        .putExtra(CardImageActivity.EXTRA_IMAGE_URL,android.net.Uri.fromFile(file).toString()))
                                }
                            }
                            AlertDialog.Builder(this).setTitle(title).setView(list).setPositiveButton(R.string.close,null).show()
                        })
                        if(group!=3) root.addView(button(getString(R.string.storage_clean)) {
                            AlertDialog.Builder(this).setTitle(title).setMessage(R.string.storage_confirm)
                                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.storage_clean) { _,_->
                                    StorageOperations.perform(this,{report,progress->StorageData.clean(applicationContext,group,report,progress)},{load()})
                                }.show()
                        }.apply { isEnabled=!snapshot.busy })
                        if(group==1) root.addView(label().apply { setText(R.string.storage_postprocess_contents) })
                        if(group==2) root.addView(label().apply { setText(R.string.storage_catalog_help) })
                        if(group==3) root.addView(button(getString(R.string.ar_downloads_title)) { startActivity(Intent(this,ArDownloadsActivity::class.java)) })
                        if(group==4) root.addView(button(getString(R.string.storage_manage_photos)) { showPhotos() }.apply { isEnabled=!snapshot.busy })
                    }
                }.onFailure { root.addView(label().apply { setText(R.string.review_operation_failed) }) }
                root.addView(button(getString(R.string.close)) { finish() })
            }
        }
    }
    private fun showPhotos() {
        val entries=PhotoScanStore.entries(this)
        AlertDialog.Builder(this).setTitle(R.string.storage_photos).setItems(entries.map {
            "${android.text.format.DateFormat.format("dd/MM/yyyy HH:mm",it.createdAt)} · ${StorageOperations.mb(java.io.File(it.imagePath).length())}"
        }.toTypedArray()) { _,index ->
            val entry=entries[index]
            AlertDialog.Builder(this).setMessage(R.string.delete_photo_scan_confirm).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.delete_photo_scan) { _,_->
                    StorageOperations.perform(this,{report,progress->PhotoScanStore.deleteMeasured(applicationContext,entry,report);progress()},{load()})
                }.show()
        }.setPositiveButton(R.string.close,null).show()
    }
}
