package io.asv.mtgocr.ocrreader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/** Camera output is full-resolution and private; imported photos keep independent detections. */
class PhotoImportActivity:AppCompatActivity() {
    class ImportState:androidx.lifecycle.ViewModel() {
        val completed=androidx.lifecycle.MutableLiveData<Boolean>()
        var started=false
    }
    private val job by lazy { androidx.lifecycle.ViewModelProvider(this)[ImportState::class.java] }
    private var cameraPath:String?=null
    private val picker=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> import(uris) }
    private val camera=registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file=cameraPath?.let(::File)
        if(ok && file?.isFile==true && file.length()>0) import(listOf(Uri.fromFile(file))) else { file?.delete();finish() }
    }
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if(ok) takePhoto() else finish() }
    override fun onCreate(state:Bundle?) {
        MagicPalette.applyTheme(this);super.onCreate(state)
        cameraPath=state?.getString("cameraPath")
        setContentView(ProgressBar(this))
        job.completed.observe(this) { failed ->
            if(failed) Toast.makeText(this,R.string.photo_import_error,Toast.LENGTH_LONG).show()
            setResult(RESULT_OK);finish()
        }
        if(state?.getBoolean("importing")==true && !job.started) { finish();return }
        if(state==null) {
            if(intent.getBooleanExtra("camera",false)) permission.launch(Manifest.permission.CAMERA)
            else picker.launch(arrayOf("image/*"))
        }
    }
    override fun onSaveInstanceState(out:Bundle) { out.putString("cameraPath",cameraPath);out.putBoolean("importing",job.started);super.onSaveInstanceState(out) }
    private fun takePhoto() {
        val file=File(File(cacheDir,"photo_capture").apply { mkdirs() },"${java.util.UUID.randomUUID()}.jpg")
        cameraPath=file.path
        try { camera.launch(FileProvider.getUriForFile(this,"$packageName.photo-files",file)) }
        catch(_:Exception) { Toast.makeText(this,R.string.photo_import_error,Toast.LENGTH_LONG).show();file.delete();finish() }
    }
    private fun import(uris:List<Uri>) {
        if(uris.isEmpty()) { finish();return }
        if(job.started)return
        job.started=true
        val retained=job
        val app=applicationContext
        val group=intent.getStringExtra("group")
        StorageOperations.worker.execute {
            var failed=false
            val ids=mutableListOf<String>()
            group?.let { ids.add(it) }
            for(uri in uris) {
                try {
                    val entry=PhotoScanStore.importPhoto(app,uri)
                    ids.add(entry.id);PhotoGroups.join(app,ids)
                } catch(_:Exception) { failed=true }
            }
            cameraPath?.let { File(it).delete() }
            retained.completed.postValue(failed)
        }
    }
}
