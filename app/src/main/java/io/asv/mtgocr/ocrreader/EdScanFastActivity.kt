package io.asv.mtgocr.ocrreader

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import io.asv.collectorvision.NativeCollectorVisionEngine
import io.asv.collectorvision.EdScanPerf
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX -> the SAME EdScan CPU engine. New rows only, provisional printing, unknown language. */
class EdScanFastActivity : AppCompatActivity() {
    private val worker=Executors.newSingleThreadExecutor()
    private val policy=EdScanFastPolicy()
    private val snapshot=AtomicBoolean(false)
    private var engine:NativeCollectorVisionEngine?=null
    private lateinit var trace:TripleScanTrace
    private lateinit var lookup:TripleCardLookup
    private lateinit var preview:PreviewView
    private lateinit var status:TextView
    private lateinit var name:TextView
    private lateinit var capture:Button
    private lateinit var savedStatus:TextView
    private lateinit var retry:Button
    private lateinit var session:TripleCollectionSession
    private val arbiter=TripleRecognitionArbiter()
    private var event=0L
    private var savedCount=0
    private var pendingCount=0
    private val failed=linkedMapOf<Long,TripleCollectionSession.State>()
    @Volatile private var acceptedVisualId:String?=null
    private var foil=false
    private var shownAt=0L
    private var provider:ProcessCameraProvider?=null
    private var analysis:ImageAnalysis?=null
    private var live:Preview?=null
    @Volatile private var active=false
    @Volatile private var ready=false
    @Volatile private var revision=0
    @Volatile private var analyzedFrames=0
    private var workerRevision=-1
    private var nextAt=0L
    private var shownId:String?=null
    private var closed=false
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) bind() else status.setText(R.string.edfast_permission)
    }

    override fun onCreate(state:Bundle?) {
        MagicPalette.applyTheme(this);super.onCreate(state)
        EdScanPerf.enabled=BuildConfig.DEBUG
        trace=TripleScanTrace(applicationContext);lookup=TripleCardLookup(applicationContext,trace::event)
        session=TripleCollectionSession(applicationContext,trace::event,"edfast",getString(R.string.edfast_group))
        trace.event("edfast.session v=1 scope=global ar=false ocr=false provisional=true language=unknown threads=2 score=0.70 margin=0.08 readings=2")
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(12,8,12,8) }
        val panel=CollectionPopupChrome.panel(this)
        root.addView(panel)
        panel.addView(CollectionPopupChrome.label(this,getString(R.string.edfast_title),20f))
        panel.addView(CollectionPopupChrome.label(this,getString(R.string.edfast_help),12f))
        panel.addView(CollectionPopupChrome.button(this,R.string.edfast_back) { finish() })
        name=CollectionPopupChrome.label(this,"").apply { setText(R.string.triple_waiting);textSize=18f;tag="edfast_name" };panel.addView(name)
        status=CollectionPopupChrome.label(this,"",12f).apply { setText(R.string.triple_loading);tag="edfast_status" };panel.addView(status)
        savedStatus=CollectionPopupChrome.label(this,"",12f).apply { tag="edfast_saved" };panel.addView(savedStatus)
        val actions=LinearLayout(this)
        actions.addView(CheckBox(this).apply { setText(R.string.foil);setTextColor(CollectionPopupChrome.color(this@EdScanFastActivity,R.attr.appGlassTextColor));setOnCheckedChangeListener { _,checked -> foil=checked } })
        retry=Button(this).apply { setText(R.string.edfast_retry);visibility=android.view.View.GONE
            setOnClickListener { val states=failed.values.toList();failed.clear();states.forEach(::save);renderSaved() }
        };actions.addView(retry);panel.addView(actions);renderSaved()
        capture=Button(this).apply {
            setText(R.string.edfast_capture);tag="edfast_capture"
            setOnClickListener { snapshot.set(true);isEnabled=false;status.setText(R.string.edfast_capture_wait) }
        };panel.addView(capture)
        preview=PreviewView(this);root.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root);CollectionScreenBackground.install(this)
        worker.execute {
            try {
                engine=NativeCollectorVisionEngine.prepareForCamera(applicationContext);ready=true
                runOnUiThread { if(!closed) bind() }
            } catch(e:Exception) {
                trace.event("edfast.error stage=prepare type=${e.javaClass.simpleName}")
                runOnUiThread { if(!closed) status.setText(R.string.triple_engine_error) }
            }
        }
    }
    private fun bind() {
        if(!active || !ready || analysis!=null || closed) return
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.CAMERA);return
        }
        val token=revision
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            if(!active || token!=revision || closed || analysis!=null) return@addListener
            try {
                val p=future.get();provider=p
                val a=ImageAnalysis.Builder().setTargetResolution(android.util.Size(1280,960))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                a.setAnalyzer(worker) { frame(it,token) }
                val v=Preview.Builder().build().apply { setSurfaceProvider(preview.surfaceProvider) }
                p.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,v,a);analysis=a;live=v
                status.setText(R.string.triple_ready)
            } catch(e:Exception) { trace.event("edfast.error stage=camera type=${e.javaClass.simpleName}");status.setText(R.string.triple_engine_error) }
        },ContextCompat.getMainExecutor(this))
    }
    private fun frame(image:ImageProxy,token:Int) {
        var bitmap:Bitmap?=null
        val started=SystemClock.elapsedRealtime()
        try {
            if(!active || token!=revision || started<nextAt) return
            if(workerRevision!=token) { policy.reset();workerRevision=token }
            val raw=image.toBitmap();val rotation=image.imageInfo.rotationDegrees
            bitmap=if(rotation==0) raw else try {
                Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(rotation.toFloat()) },true)
            } finally { raw.recycle() }
            val decoded=SystemClock.elapsedRealtime()
            val scan=engine?.scan(requireNotNull(bitmap)) ?: return
            val completed=SystemClock.elapsedRealtime();analyzedFrames++
            if(!active || token!=revision) return
            val top=scan.hits.firstOrNull();val runner=scan.hits.getOrNull(1)
            val reading=policy.observe(scan.present,top?.cardId,top?.score,runner?.score,started,completed)
            val photo=if(reading.confirmed && top?.cardId!=acceptedVisualId && scan.corners.size==4) {
                val crop=Bitmap.createBitmap(630,880,Bitmap.Config.ARGB_8888)
                try {
                    val transform=Matrix()
                    val points=scan.corners.flatMap { listOf(it.x*bitmap.width,it.y*bitmap.height) }.toFloatArray()
                    if(transform.setPolyToPoly(points,0,floatArrayOf(0f,0f,630f,0f,630f,880f,0f,880f),0,4)) {
                        android.graphics.Canvas(crop).drawBitmap(bitmap,transform,android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                        java.io.ByteArrayOutputStream().use { out -> crop.compress(Bitmap.CompressFormat.JPEG,90,out);out.toByteArray() }
                    } else null
                } finally { crop.recycle() }
            } else null
            val pm=getSystemService(POWER_SERVICE) as android.os.PowerManager
            trace.event("edfast.frame captureAtMs=$started sensorTimestampNs=${image.imageInfo.timestamp} reason=${reading.reason} confirmed=${reading.confirmed} candidateSpanMs=${reading.latencyMs} frameMs=${completed-started} decodeMs=${decoded-started} detectMs=${scan.detectionMs} featureMs=${scan.recognitionMs-scan.searchMs} searchMs=${scan.searchMs} rows=${scan.searchedRows} sharpness=${scan.sharpness} width=${bitmap.width} height=${bitmap.height} thermal=${if(android.os.Build.VERSION.SDK_INT>=29) pm.currentThermalStatus else -1} hits=${scan.hits.joinToString(",") { "${it.cardId}:${it.score}" }}")
            if(snapshot.getAndSet(false)) {
                try {
                    val dir=File(filesDir,"edfast-diagnostics").apply { mkdirs() }
                    val key=System.currentTimeMillis().toString()
                    File(dir,"$key.jpg").outputStream().use { requireNotNull(bitmap).compress(Bitmap.CompressFormat.JPEG,90,it) }
                    File(dir,"$key.txt").writeText("captureAtMs=$started reason=${reading.reason} sharpness=${scan.sharpness} corners=${scan.corners} hits=${scan.hits}")
                    dir.listFiles().orEmpty().filter { it.extension=="jpg" }.sortedByDescending { it.name }.drop(30).forEach {
                        it.delete();File(dir,"${it.nameWithoutExtension}.txt").delete()
                    }
                    trace.event("edfast.snapshot key=$key captureAtMs=$started")
                    runOnUiThread { if(active && token==revision) Toast.makeText(this,R.string.edfast_capture_saved,Toast.LENGTH_SHORT).show() }
                } catch(e:Exception) {
                    trace.event("edfast.error stage=snapshot type=${e.javaClass.simpleName}")
                    runOnUiThread { if(active && token==revision) Toast.makeText(this,R.string.edfast_capture_failed,Toast.LENGTH_LONG).show() }
                }
            }
            runOnUiThread {
                if(!active || token!=revision) return@runOnUiThread
                capture.isEnabled=!snapshot.get()
                status.text=getString(R.string.edfast_sample,completed-started,top?.score ?: 0f,
                    if(top!=null && runner!=null) top.score-runner.score else 0f,reading.reason)
                val id=top?.cardId.takeIf { scan.present }
                shownAt=started
                if(!scan.present) {
                    arbiter.missing(started)
                    if(!arbiter.hasConfirmedScene) acceptedVisualId=null
                } else arbiter.present()
                if(shownId!=id || (top?.score ?: 0f)<.50f) { shownId=id;name.setText(R.string.triple_waiting) }
                // Resolve outside camera worker. Only a fresh confirmed capture may create a row.
                val selectedFinish=if(foil) "foil" else "nonfoil"
                if(id!=null && (top?.score ?: 0f)>=.50f) lookup.lookup(id) { data ->
                    if(!active || token!=revision || shownId!=id || shownAt!=started || data==null) return@lookup
                    name.text=getString(if(reading.confirmed) R.string.edfast_confirmed else R.string.edfast_candidate,data.name)
                    if(reading.confirmed && photo!=null && pendingCount+failed.size<8) {
                        val decision=arbiter.observe(TripleReadingPolicy.identity(data.name),"EdScan",true,started,SystemClock.elapsedRealtime())
                        if(decision.accepted) acceptedVisualId=id
                        if(decision.accepted && decision.newEncounter) {
                            val target=TripleScanActivity.Target(id,data.printing,data.name,data.set,data.name,data.image,selectedFinish,"EdScan",true,data.rarity)
                            val state=TripleCollectionSession.State(++event,target,null,selectedFinish,1,photo,false,java.util.UUID.randomUUID().toString())
                            trace.event("edfast.accept event=$event captureAtMs=$started candidateSpanMs=${reading.latencyMs} metadataAgeMs=${SystemClock.elapsedRealtime()-started}")
                            save(state)
                        }
                    }
                }
            }
            nextAt=SystemClock.elapsedRealtime()+150L // bounded duty; no queue of old frames
        } catch(e:Exception) {
            policy.reset();trace.event("edfast.error stage=frame type=${e.javaClass.simpleName}")
        } finally { bitmap?.recycle();image.close() }
    }
    private fun renderSaved() {
        savedStatus.text=getString(R.string.edfast_saved,savedCount,pendingCount,failed.size)
        retry.visibility=if(failed.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }
    private fun save(state:TripleCollectionSession.State) {
        if(BuildConfig.DEBUG && intent.getBooleanExtra("triple_diagnostic_only",false)) return
        pendingCount++;renderSaved()
        session.submit(state) { card,_,error ->
            pendingCount--
            if(error==null && card!=null) savedCount++ else failed[state.event]=state
            if(!closed) { renderSaved();if(error!=null) Toast.makeText(this,R.string.triple_save_failed,Toast.LENGTH_LONG).show() }
        }
    }
    override fun onStart() { super.onStart();active=true;trace.event("edfast.lifecycle state=start");io.asv.collectorvision.EdScanInferencePriority.acquire(this);bind() }
    override fun onStop() {
        trace.event("edfast.lifecycle state=stop frames=$analyzedFrames pending=$pendingCount");active=false;revision++;snapshot.set(false);capture.isEnabled=true;shownId=null;arbiter.resetPending()
        analysis?.clearAnalyzer();val owned=listOfNotNull(analysis,live);if(owned.isNotEmpty()) provider?.unbind(*owned.toTypedArray())
        analysis=null;live=null;io.asv.collectorvision.EdScanInferencePriority.release(this);super.onStop()
    }
    override fun onDestroy() {
        closed=true;lookup.close();worker.execute { engine?.close();engine=null;trace.close() };worker.shutdown();super.onDestroy()
    }
}
