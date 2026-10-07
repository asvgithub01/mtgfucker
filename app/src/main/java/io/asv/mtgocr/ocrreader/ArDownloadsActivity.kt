package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import java.util.concurrent.atomic.AtomicBoolean

class ArDownloadsActivity:AppCompatActivity() {
    private data class Row(val id:String,val name:String,val published:ArPackageStore.Entry?)
    private var entries=emptyList<Row>()
    private val selected=linkedSetOf<String>()
    private lateinit var list:ListView
    private lateinit var search:EditText
    private lateinit var status:TextView
    private lateinit var download:Button
    private lateinit var refresh:Button
    private lateinit var progress:ProgressBar
    private val cancel=AtomicBoolean(false)
    private var busy=false
    private var coverage=emptyMap<String,String>()
    private val cameraPermission=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) startDownloads(true) else status.setText(R.string.ar_generation_unavailable)
    }
    private val chooser get()=intent.getBooleanExtra("choose",false)
    override fun onCreate(state:Bundle?) {
        MagicPalette.applyTheme(this);super.onCreate(state)
        if(!PremiumAccess.isEnabled(this)) { Toast.makeText(this,R.string.premium_required,Toast.LENGTH_LONG).show();finish();return }
        selected.addAll(state?.getStringArrayList("selected") ?: listOfNotNull(intent.getStringExtra("package_id")))
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(20,16,20,16) }
        root.addView(TextView(this).apply { setText(R.string.ar_downloads_title);textSize=22f })
        root.addView(TextView(this).apply { setText(R.string.ar_downloads_help) })
        search=EditText(this).apply { setHint(R.string.edition_search_hint);setSingleLine() };root.addView(search)
        val actions=LinearLayout(this);root.addView(actions)
        actions.addView(Button(this).apply { tag="ar_select_all";setText(R.string.ar_select_all);visibility=if(chooser) View.GONE else View.VISIBLE;setOnClickListener { if(!busy) { selected.addAll(filtered().map { it.id });render() } } },LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Button(this).apply { tag="ar_clear_selection";setText(R.string.ar_clear_selection);setOnClickListener { if(!busy) { selected.clear();render() } } },LinearLayout.LayoutParams(0,-2,1f))
        status=TextView(this);root.addView(status)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100;visibility=View.GONE };root.addView(progress)
        list=ListView(this);root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        download=Button(this).apply { setText(if(chooser) R.string.ar_use_package else R.string.ar_download_selected);setOnClickListener { startDownloads() } };root.addView(download)
        refresh=Button(this).apply { setText(R.string.ar_refresh_catalog);setOnClickListener { load() } };root.addView(refresh)
        root.addView(Button(this).apply { setText(android.R.string.cancel);setOnClickListener { if(busy) cancel.set(true) else finish() } })
        setContentView(root);search.doAfterTextChanged { render() };load()
    }
    private fun filtered()=entries.filter { EditionSearch.matches(it.id,it.name,search.text.toString()) }
    private fun render() {
        val visible=filtered()
        val rows=visible.map { e ->
            val installed=ArPackageStore.installedVersion(this,e.id)
            val ePackage=e.published
            val state=getString(if(installed!=null && (ePackage==null || installed==ePackage.version)) R.string.ar_downloaded else if(installed!=null) R.string.ar_update_available else if(ePackage==null) R.string.ar_generate_missing else R.string.ar_not_downloaded)
            val detail=ePackage?.let { "${it.count} · ${"%.1f".format((it.database.bytes+it.metadata.bytes)/1000000.0)} MB\n${it.coverage}" } ?: coverage[e.id] ?: getString(R.string.ar_main_set_only)
            EditionSearchAdapter.Entry(e.id,e.name,"${if(e.id in selected) "☑" else "☐"} $state · $detail")
        }
        list.adapter=EditionSearchAdapter(this,rows)
        list.setOnItemClickListener { _,_,position,_ ->
            if(!busy) { val id=visible[position].id; if(chooser) selected.clear();if(!selected.add(id)) selected.remove(id);render() }
        }
        download.isEnabled=!busy && selected.any { id->entries.any { it.id==id } }
        refresh.isEnabled=!busy
    }
    private fun load() {
        busy=true;status.setText(R.string.ar_loading_catalog);render()
        val app=applicationContext
        ArPackageStore.worker.execute {
            val result=runCatching { ArPackageStore.catalog(app) }
            val configuration=org.json.JSONObject(app.assets.open("ar-editions.json").bufferedReader().use { it.readText() })
            val knownCoverage=configuration.keys().asSequence().associateWith { configuration.getJSONObject(it).getString("coverage") }
            val sets=runCatching { ArLocalPackageBuilder.editions(app) }.getOrDefault(emptyList())
            val cached=if(result.isFailure) ArPackageStore.cachedCatalog(app) else emptyList()
            runOnUiThread { if(!isDestroyed) {
                busy=false;coverage=knownCoverage
                val published=result.getOrDefault(cached)
                entries=(published.map { Row(it.id,it.name,it) }+ArPackageStore.installedEntries(app).map { Row(it.id,it.name,null) }+sets.map { Row(it.id,it.name,null) }).distinctBy { it.id }.sortedBy { it.name }
                status.setText(if(result.isSuccess) R.string.ar_catalog_ready else R.string.ar_catalog_error);render()
            } }
        }
    }
    private fun startDownloads(confirmed:Boolean=false) {
        if(!PremiumAccess.isEnabled(this)) { finish();return }
        val requested=entries.filter { it.id in selected };if(requested.isEmpty()) return
        val missing=requested.filter { it.published==null }
        if(missing.isNotEmpty() && !confirmed) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.ar_generate_missing)
                .setMessage(getString(R.string.ar_generation_warning,missing.size))
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok) { _,_->startDownloads(true) }.show()
            return
        }
        if(missing.isNotEmpty()) {
            if(androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                cameraPermission.launch(android.Manifest.permission.CAMERA);return
            }
            try {
                if(com.google.ar.core.ArCoreApk.getInstance().requestInstall(this,true)==com.google.ar.core.ArCoreApk.InstallStatus.INSTALL_REQUESTED) return
            } catch(error:Exception) { status.setText(R.string.ar_generation_unavailable);return }
        }
        cancel.set(false);busy=true;progress.visibility=View.VISIBLE;render()
        val app=applicationContext
        ArPackageStore.worker.execute {
            var lastPost=0L
            val result=runCatching {
                requested.forEachIndexed { index,e ->
                    check(!cancel.get())
                    val report:(Long,Long)->Unit={ bytes,total ->
                        check(!cancel.get())
                        val now=android.os.SystemClock.elapsedRealtime()
                        if(now-lastPost>100 || bytes==total) {
                            lastPost=now
                            runOnUiThread { if(!isDestroyed) {
                                status.text=getString(if(e.published==null) R.string.ar_generation_progress else R.string.ar_download_progress,e.name,index+1,requested.size)
                                progress.progress=(bytes*100/total.coerceAtLeast(1)).toInt()
                            } }
                        }
                    }
                    report(0,1)
                    if(e.published!=null) ArPackageStore.install(app,e.published,progress=report)
                    else if(runCatching { ArPackageStore.installed(app,e.id) }.getOrNull()==null) {
                        ArLocalPackageBuilder.generate(app,ArLocalPackageBuilder.Edition(e.id,e.name),{ check(!cancel.get()) },report)
                    }
                }
            }
            runOnUiThread { if(!isDestroyed) {
                busy=false;progress.visibility=View.GONE
                status.setText(if(result.isSuccess) R.string.ar_download_complete else if(cancel.get()) R.string.ar_download_cancelled else R.string.ar_download_error)
                if(result.isFailure && !cancel.get()) {
                    android.util.Log.w("ArPackages","Package preparation failed",result.exceptionOrNull())
                    if(result.exceptionOrNull()?.message==getString(R.string.ar_generation_limit)) status.setText(R.string.ar_generation_limit)
                }
                render()
                if(result.isSuccess && chooser) { setResult(RESULT_OK,Intent().putExtra("package_id",requested.single().id));finish() }
            } }
        }
    }
    override fun onSaveInstanceState(out:Bundle) { out.putStringArrayList("selected",ArrayList(selected));super.onSaveInstanceState(out) }
    override fun onDestroy() { cancel.set(true);super.onDestroy() }
}
