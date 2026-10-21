package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.app.Application
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** One deletion at a time; dialogs follow the visible Activity and results survive background completion. */
object StorageOperations {
    val worker=Executors.newSingleThreadExecutor()
    val lock=Any()
    private val main=Handler(Looper.getMainLooper())
    private var current=WeakReference<Activity>(null)
    private val updateQueued=java.util.concurrent.atomic.AtomicBoolean(false)
    private var dialog:AlertDialog?=null
    private var progressText:TextView?=null
    @Volatile private var running:StorageData.Report?=null
    fun initialize(app:Application) {
        app.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a:Activity) { current=WeakReference(a);show(a) }
            override fun onActivityPaused(a:Activity) { if(current.get()===a) { dialog?.dismiss();dialog=null;progressText=null;current.clear() } }
            override fun onActivityCreated(a:Activity,b:Bundle?) {}
            override fun onActivityStarted(a:Activity) {}
            override fun onActivityStopped(a:Activity) {}
            override fun onActivitySaveInstanceState(a:Activity,b:Bundle) {}
            override fun onActivityDestroyed(a:Activity) {}
        })
    }
    private fun show(a:Activity) {
        if(a.isFinishing || a.isDestroyed)return
        val report=running
        if(report!=null) {
            if(dialog==null) {
                val panel=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL;setPadding(32,24,32,24) }
                panel.addView(ProgressBar(a))
                progressText=TextView(a);panel.addView(progressText)
                dialog=AlertDialog.Builder(a).setTitle(R.string.storage_deleting).setView(panel).setCancelable(false).create()
                dialog!!.show()
            }
            progressText?.text=a.getString(R.string.storage_progress,report.files,mb(report.bytes))
        } else {
            dialog?.dismiss();dialog=null;progressText=null
            // Legacy results did not distinguish manual and automatic work. Keep them
            // available in Settings, but never replay them as unsolicited dialogs.
            val prefs=a.getSharedPreferences("storage_manual_results",0)
            val message=prefs.getString("pending",null) ?: return
            prefs.edit().clear().apply()
            AlertDialog.Builder(a).setTitle(R.string.storage_result).setMessage(message).setPositiveButton(android.R.string.ok,null).show()
        }
    }
    fun mb(bytes:Long)=String.format(java.util.Locale.getDefault(),"%.2f MB",bytes/1_000_000.0)
    private fun update() {
        if(updateQueued.compareAndSet(false,true)) main.postDelayed({ updateQueued.set(false);current.get()?.let(::show) },50)
    }
    private fun finish(context:Context,report:StorageData.Report,always:Boolean) {
        running=null
        if(always || report.files>0 || report.failed>0) {
            val prefs=context.getSharedPreferences("storage_manual_results",0)
            val accumulated=prefs.contains("pending")
            val bytes=report.bytes+if(accumulated) prefs.getLong("bytes",0) else 0L
            val files=report.files+if(accumulated) prefs.getInt("files",0) else 0
            val failed=report.failed+if(accumulated) prefs.getInt("failed",0) else 0
            val blocked=report.blocked || (accumulated && prefs.getBoolean("blocked",false))
            val message=context.getString(R.string.storage_result_detail,files,mb(bytes),failed)+
                if(blocked) "\n"+context.getString(R.string.storage_busy) else ""
            prefs.edit().putString("pending",message).putLong("bytes",bytes).putInt("files",files)
                .putInt("failed",failed).putBoolean("blocked",blocked).commit()
        }
        update()
    }
    fun perform(context:Context,action:(StorageData.Report,()->Unit)->Unit,done:()->Unit={}) {
        val app=context.applicationContext
        worker.execute { synchronized(lock) {
            val report=StorageData.Report();running=report;update()
            try { action(report,::update) } catch(_:Exception) { report.failed++ }
            finally { finish(app,report,true);main.post(done) }
        } }
    }
    /** Automatic work never owns a dialog, including progress, failures and retries. */
    fun automatic(context:Context,action:((Long)->Unit)->Unit) = synchronized(lock) {
        val report=StorageData.Report()
        try { action { bytes -> if(bytes<0) report.failed++ else { report.bytes+=bytes;report.files++ } } }
        catch(e:Exception) { report.failed++;throw e }
        finally { saveAutomaticResult(context,report) }
    }
    private fun saveAutomaticResult(context:Context,report:StorageData.Report) {
        if(report.files==0 && report.failed==0 && !report.blocked)return
        val message=context.getString(R.string.storage_result_detail,report.files,mb(report.bytes),report.failed)+
            if(report.blocked) "\n"+context.getString(R.string.storage_busy) else ""
        context.getSharedPreferences("storage_results",0).edit().putString("pending",message)
            .putLong("bytes",report.bytes).putInt("files",report.files).putInt("failed",report.failed)
            .putBoolean("blocked",report.blocked).commit()
    }
    @JvmStatic fun afterCollectionDelete(context:Context,cards:List<io.asv.mtgocr.ocrreader.model.CardInfo>) {
        val snapshots=cards.map { it.snapshotForPersistence() }
        worker.execute { synchronized(lock) {
            val report=StorageData.Report()
            try {
            val targets=snapshots.flatMap { card -> StorageData.imageNames(card.imgPath.orEmpty())+
                card.scanPhotoPaths.filter { it.isNotBlank() }.map { java.io.File(context.filesDir,it).canonicalPath } }.toSet()
            StorageData.clean(context,0,report,{},targets)
            if(!report.blocked) StorageData.clean(context,1,report,{},targets)
            if(report.blocked) {
                val pending=context.getSharedPreferences("storage_pending",0)
                val all=pending.getStringSet("targets",emptySet()).orEmpty()+targets
                pending.edit().putBoolean("images",true).putStringSet("targets",all).commit()
                PostprocessCleanup.schedule(context,LibraryCatalog.activeFile(context))
            }
            } catch(_:Exception) { report.failed++ }
            finally { saveAutomaticResult(context,report) }
        } }
    }
}

class StorageApplication:Application() {
    override fun onCreate() { super.onCreate();StorageOperations.initialize(this) }
}
