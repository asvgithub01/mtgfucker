package io.asv.collectorvision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/** Native experimental scanner. Deliberately has no dependency on the app's library/database. */
open class NativeCollectorVisionActivity : AppCompatActivity() {
    /** Host integration stays outside this module; callback confirms durable persistence. */
    protected open val usesExternalCamera: Boolean get() = false
    @Volatile private var externalAnalyzedFrames=0
    private val externalFrameBusy=AtomicBoolean(false)
    protected val acceptsExternalFrame: Boolean get() = usesExternalCamera && active && ready && !idlePaused && !externalFrameBusy.get()
    /** Transfers frame ownership only on true; converts on the existing serial inference worker. */
    protected fun submitExternalFrame(rotation: Int, decode: () -> Bitmap, release: () -> Unit): Boolean {
        if(!acceptsExternalFrame || !externalFrameBusy.compareAndSet(false,true)) return false
        val token=generation
        val queued=System.nanoTime()
        try {
            worker.execute {
                EdScanPerf.record("AR.workerQueue",System.nanoTime()-queued)
                var released=false
                try {
                    if(active && token==generation && !idlePaused) {
                        val started=android.os.SystemClock.elapsedRealtime()
                        val bitmap=EdScanPerf.measure("AR.decode") { decode() }
                        val decoded=android.os.SystemClock.elapsedRealtime()
                        release(); released=true
                        EdScanPerf.measure("AR.pipeline") { analyzeBitmap(bitmap,rotation,token) }
                        if(externalAnalyzedFrames%15==0) android.util.Log.d("EdScanFrame",
                            "AR decode=${decoded-started}ms pipeline=${android.os.SystemClock.elapsedRealtime()-decoded}ms")
                    }
                } catch(error: Exception) {
                    android.util.Log.w("EdScanFrame","External frame failed",error)
                } finally {
                    if(!released) release()
                    externalFrameBusy.set(false)
                }
            }
        } catch(_: java.util.concurrent.RejectedExecutionException) { externalFrameBusy.set(false); return false }
        return true
    }
    protected open val autoStartEnabled: Boolean get() = false
    protected open val autoSaveEnabled: Boolean get() = false
    protected open val sessionLabel: String? get() = null
    protected open fun addHostControls(container: LinearLayout) = Unit
    protected open fun scanningStateChanged(running: Boolean) = Unit
    protected open fun addHostRecentControls(container: LinearLayout) = Unit
    /** Speculative preparation only: must not display or save an unaccepted candidate. */
    protected open fun prepareVisualCandidate(cardId: String) = Unit
    protected open fun acceptedDebug(key: String) = Unit
    protected open fun printingIdentities(): Map<String,String> = emptyMap()
    protected open fun requestPrintingIdentities(ids: List<String>) = Unit
    protected open fun acceptedProvisional(ids: List<String>) = Unit
    @Volatile private var visualScope: Set<String>? = null
    @Volatile private var scopeVersion=0
    protected fun updateVisualScope(ids: Set<String>?) {
        visualScope=ids?.toSet(); scopeVersion++; acceptance.resetStability()
        if (::status.isInitialized) {
            if (idlePaused) status.setText(R.string.cv_native_idle_paused)
            else if (ids != null && ids.isEmpty()) status.setText(R.string.cv_native_scope_wait)
        }
    }
    @Volatile private var idlePaused=false
    private var lastUse=0L
    private var lastUsefulCard: String?=null
    private val idleHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var heartbeatExpected=0L
    private val heartbeat=object:Runnable {
        override fun run() {
            if(!active || !EdScanPerf.enabled) return
            val now=SystemClock.elapsedRealtime()
            EdScanPerf.record("UI.heartbeatLate",(now-heartbeatExpected).coerceAtLeast(0)*1_000_000)
            val pm=getSystemService(POWER_SERVICE) as android.os.PowerManager
            val battery=registerReceiver(null,android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            EdScanPerf.event("power saver=${pm.isPowerSaveMode} thermal=${if(android.os.Build.VERSION.SDK_INT>=29) pm.currentThermalStatus else -1} battery=${battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1)} temperatureTenthsC=${battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE,-1)}")
            heartbeatExpected=SystemClock.elapsedRealtime()+3000
            idleHandler.postDelayed(this,3000)
        }
    }
    private val idleTick=object:Runnable {
        override fun run() {
            if(!active || idlePaused) return
            if(ScannerIdlePolicy.shouldPause(SystemClock.elapsedRealtime(),lastUse,automaticIdlePauseEnabled)) pauseScanner()
            else idleHandler.postDelayed(this,1000)
        }
    }
    protected val scanSavePending: Boolean get()=acceptance.isPending()
    protected fun manualScanFeedback() { playScanTone(ToneGenerator.TONE_PROP_BEEP,80) }
    protected fun pauseScanner() {
        EdScanPerf.event("pause automaticAllowed=$automaticIdlePauseEnabled idleMs=${SystemClock.elapsedRealtime()-lastUse}")
        idlePaused=true; generation++; acceptance.resetStability()
        analysis?.clearAnalyzer()
        val bound=listOfNotNull(analysis,preview)
        if(bound.isNotEmpty()) provider?.unbind(*bound.toTypedArray())
        analysis=null; preview=null
        EdScanInferencePriority.release(this); scanningStateChanged(false)
        status.setText(R.string.cv_native_idle_paused)
        start.setText(R.string.cv_native_resume); start.visibility=if(scanResumeAllowed) View.VISIBLE else View.GONE; start.isEnabled=scanResumeAllowed
    }
    protected open val automaticIdlePauseEnabled: Boolean get() = true
    protected open val scanResumeAllowed: Boolean get() = true
    protected fun resumeScanner() {
        if (!scanResumeAllowed) return
        idlePaused=false; lastUse=SystemClock.elapsedRealtime()
        EdScanInferencePriority.acquire(this)
        idleHandler.removeCallbacks(idleTick); idleHandler.postDelayed(idleTick,1000)
        if(ready) { start.visibility=View.GONE; requestCamera() } else prepare()
    }
    override fun onUserInteraction() { super.onUserInteraction(); lastUse=SystemClock.elapsedRealtime() }

    private val debugFrames = java.util.ArrayDeque<JSONObject>()
    private var priceScanKey: String? = null
    protected open fun acceptedPhoto(key: String?) = Unit
    protected open fun acceptedPreview(bitmap: Bitmap) = Unit
    protected open fun persistCandidate(cardId: String, score: Float, callback: (Boolean) -> Unit) { callback(false) }
    protected open val persistenceFailureMessage: String? get() = null
    protected fun resolvedAcceptedFinish(id: String, finish: String) {
        if (priceCardId == id) { recognizedFinish = finish; renderRecognizedPrice() }
    }
    protected open val hostProvidesPrices: Boolean get() = false
    protected fun requestAcceptedPrice(id: String) { loadMetadata(id, generation) }
    protected fun resolvedLocalPrice(id: String, finish: String, label: String, amount: Double, currency: String, euros: Double, source: String) {
        if (priceCardId != id) return
        val suffix = when (finish) { "foil" -> "_foil"; "etched" -> "_etched"; else -> "" }
        metadataCache[id] = label
        priceSources[id] = source
        priceCache[id] = (priceCache[id] ?: JSONObject()).put(currency.lowercase() + suffix, amount).put("eur" + suffix, euros)
        metadataFailed.remove(id)
        renderRecognizedPrice()
    }
    protected open val priceFinish: String get() = "nonfoil"
    protected open val priceCurrency: String get() = "eur"
    private lateinit var priceView: TextView
    @Volatile private var savePhotos = false
    @Volatile private var showThumbnail = true
    private var priceCardId: String? = null
    private var recognizedFinish = "nonfoil"
    private var recognizedCurrency = "eur"
    private val priceCache = mutableMapOf<String, JSONObject>()
    private val priceSources = mutableMapOf<String, String>()
    private val acceptance = CollectorVisionAcceptanceGate()
    private var tone: ToneGenerator? = null
    private lateinit var saveStatus: TextView
    private lateinit var previewView: PreviewView
    private var preview: Preview? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val metadataWorker = Executors.newSingleThreadExecutor()
    private val preparing = AtomicBoolean(false)
    private var engine: NativeCollectorVisionEngine? = null // worker-confined
    private var preparation: Future<*>? = null
    private lateinit var frameView: ImageView
    private lateinit var status: TextView
    private lateinit var resultView: TextView
    private lateinit var start: Button
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    @Volatile private var active = false
    @Volatile private var ready = false
    @Volatile private var generation = 0
    private var shownBitmap: Bitmap? = null // UI-confined
    private var candidate: String? = null // worker-confined
    private var repetitions = 0
    private var lastFrameFinishedAt = 0L
    private var workerGeneration = -1
    private var lastDiagnosticAt = 0L
    private val diagnosticKey = java.util.UUID.randomUUID().toString()
    private var lastEvidenceAt = 0L
    private var displayedId: String? = null // UI-confined
    private val metadataCache = mutableMapOf<String, String>() // UI-confined
    private val metadataFailed = mutableSetOf<String>()
    private val metadataPending = mutableSetOf<String>()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) bindCamera() else status.setText(R.string.cv_native_permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdScanPerf.enabled=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
        EdScanPerf.event("scanner external=$usesExternalCamera autoIdle=$automaticIdlePauseEnabled")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12) }
        root.addView(TextView(this).apply { setText(R.string.cv_native_title); textSize = 20f })
        sessionLabel?.let { label -> root.addView(TextView(this).apply { text = label; textSize = 16f }) }
        addHostControls(root)
        if (autoSaveEnabled) {
            val prefs = getSharedPreferences("cornelius_scanner", MODE_PRIVATE)
            val enabled = prefs.getBoolean("safety_filters", true)
            acceptance.setFiltersEnabled(enabled)
            EdScanTuning.read(this).apply(acceptance)
            val modeHint = TextView(this).apply {
                textSize = 12f
                text = if (enabled) getString(R.string.cv_native_filtered_hint) else EdScanTuning.read(this@NativeCollectorVisionActivity).description(this@NativeCollectorVisionActivity)
            }
            root.addView(CheckBox(this).apply {
                tag = "cornelius_safety_filters"
                setText(R.string.cv_native_safety_filters)
                isChecked = enabled
                setOnCheckedChangeListener { _, checked ->
                    acceptance.setFiltersEnabled(checked)
                    prefs.edit().putBoolean("safety_filters", checked).apply()
                    modeHint.text = if (checked) getString(R.string.cv_native_filtered_hint) else EdScanTuning.read(this@NativeCollectorVisionActivity).description(this@NativeCollectorVisionActivity)
                }
            })
            val modeRow = LinearLayout(this)
            modeRow.addView(modeHint, LinearLayout.LayoutParams(0, -2, 1f))
            modeRow.addView(Button(this).apply {
                setText(R.string.edscan_tuning_title)
                setOnClickListener { EdScanTuning.show(this@NativeCollectorVisionActivity) { tuning ->
                    tuning.apply(acceptance)
                    if (!prefs.getBoolean("safety_filters", true)) modeHint.text=tuning.description(this@NativeCollectorVisionActivity)
                } }
            })
            root.addView(modeRow)
        }
        val controls = LinearLayout(this)
        start = Button(this).apply { setText(R.string.cv_native_start); setOnClickListener { resumeScanner() } }
        // Retain the button only as a recovery action after preparation fails.
        start.visibility = View.GONE
        controls.addView(start, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply { setText(R.string.cv_native_close); setOnClickListener { finish() } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply {
            setText(R.string.party_settings); setOnClickListener { PriceCelebration.showSettings(this@NativeCollectorVisionActivity) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controls)
        status = TextView(this).apply { setText(R.string.cv_native_idle); textSize = 12f }
        root.addView(status)
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            setBackgroundColor(Color.BLACK)
            contentDescription = getString(R.string.cv_native_live_preview)
        }
        root.addView(previewView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val thumbnailPrefs = getSharedPreferences("cornelius_scanner", MODE_PRIVATE)
        savePhotos = thumbnailPrefs.getBoolean("save_photos", false)
        root.addView(CheckBox(this).apply {
            tag = "edscan_save_photos"
            setText(R.string.edscan_save_photos)
            isChecked = savePhotos
            setOnCheckedChangeListener { _, checked ->
                savePhotos = checked
                thumbnailPrefs.edit().putBoolean("save_photos", checked).apply()
            }
        })
        showThumbnail = thumbnailPrefs.getBoolean("show_thumbnail", true)
        root.addView(CheckBox(this).apply {
            tag = "cornelius_show_thumbnail"
            setText(R.string.cv_native_show_thumbnail)
            isChecked = showThumbnail
            setOnCheckedChangeListener { _, checked ->
                showThumbnail = checked
                thumbnailPrefs.edit().putBoolean("show_thumbnail", checked).apply()
                frameView.visibility = if (checked) View.VISIBLE else View.GONE
                if (!checked) {
                    frameView.setImageDrawable(null)
                    shownBitmap?.recycle(); shownBitmap = null
                }
            }
        })
        val processedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        frameView = ImageView(this).apply { visibility = if (showThumbnail) View.VISIBLE else View.GONE; scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.BLACK) }
        processedRow.addView(frameView, LinearLayout.LayoutParams((90 * resources.displayMetrics.density).toInt(),
            (110 * resources.displayMetrics.density).toInt()))
        priceView = TextView(this).apply { tag = "cornelius_price"; setText(R.string.cv_native_price_idle); textSize = 16f }
        processedRow.addView(priceView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(processedRow)
        addHostRecentControls(root)
        saveStatus = TextView(this).apply {
            if (autoSaveEnabled) setText(R.string.cv_native_save_ready)
            textSize = 14f
        }
        root.addView(saveStatus)
        resultView = TextView(this).apply { setText(R.string.cv_native_no_card); textSize = 16f }
        root.addView(resultView)
        setContentView(root)
        EdScanInferencePriority.acquire(this)
        EdScanJobs.resume(applicationContext)
        if (autoStartEnabled) prepare()
    }

    private fun prepare() {
        if (ready) { requestCamera(); return }
        if (!preparing.compareAndSet(false, true)) return
        start.isEnabled = false
        status.setText(R.string.cv_native_loading)
        preparation = worker.submit {
            try {
                engine = NativeCollectorVisionEngine.prepare(applicationContext) { message ->
                    runOnUiThread { if (!isDestroyed) status.text = message }
                }
                ready = true
                runOnUiThread { if (!isDestroyed) { start.isEnabled = true; requestCamera() } }
            } catch (e: Exception) {
                runOnUiThread { if (!isDestroyed) { start.isEnabled = true; start.visibility = View.VISIBLE; status.text = getString(R.string.cv_native_error, e.message.orEmpty()) } }
            } finally { preparing.set(false) }
        }
    }

    private fun requestCamera() {
        if (!active || idlePaused) return
        if(usesExternalCamera) {
            scanningStateChanged(true); start.visibility=View.GONE; status.setText(R.string.cv_native_running); return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) bindCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun bindCamera() {
        if (usesExternalCamera || !active || idlePaused || !ready || analysis != null) return
        val token = generation
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!active || token != generation || analysis != null) return@addListener
            try {
                val p = future.get()
                provider = p
                val a = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 960))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                a.setAnalyzer(worker) { image -> analyze(image, token) }
                val live = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
                p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, live, a)
                preview = live
                analysis = a
                scanningStateChanged(true)
                status.setText(R.string.cv_native_running)
            } catch (e: Exception) { status.text = getString(R.string.cv_native_error, e.message.orEmpty()) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy, token: Int) {
        try {
            if(!active || token!=generation) return
            val rotation=image.imageInfo.rotationDegrees
            val bitmap=EdScanPerf.measure("CameraX.decode") {
                val raw=image.toBitmap()
                if(rotation==0) raw else Bitmap.createBitmap(raw,0,0,raw.width,raw.height,
                    Matrix().apply { postRotate(rotation.toFloat()) },true).also { if(it!==raw) raw.recycle() }
            }
            EdScanPerf.measure("CameraX.pipeline") { analyzeBitmap(bitmap,rotation,token) }
        } catch(error: Exception) {
            android.util.Log.w("EdScanFrame","CameraX frame failed",error)
            runOnUiThread { if(active && token==generation) { acceptance.resetStability(); status.text=getString(R.string.cv_native_error,error.message.orEmpty()) } }
        } finally { image.close() }
    }

    private fun analyzeBitmap(bitmap: Bitmap, rotation: Int, token: Int) {
        try {
            if (!active || token != generation) return
            val now = SystemClock.elapsedRealtime()
            // Reset for a real capture gap, not for time spent running the models.
            val captureGap = workerGeneration != token || now - lastFrameFinishedAt > 1500
            if (captureGap) { candidate = null; repetitions = 0 }
            workerGeneration = token
            val currentScope=scopeVersion
            val allowed=visualScope
            if(allowed!=null && allowed.isEmpty()) return
            val result = engine?.scan(bitmap,allowed) ?: return
            if(usesExternalCamera) externalAnalyzedFrames++
            val top = result.hits.firstOrNull()
            val id = if (result.present && top != null && top.score >= 0.50f) top.cardId else null
            if (candidate == id && id != null) repetitions++ else { candidate = id; repetitions = if (id == null) 0 else 1 }
            val stable = repetitions >= 2
            if (now - lastDiagnosticAt >= 2000) {
                lastDiagnosticAt = now
                android.util.Log.i("EdScanFrame", "detect=${result.detectionMs} recognize=${result.recognitionMs} score=${top?.score} margin=${top?.score?.minus(result.hits.getOrNull(1)?.score ?: Float.NaN)} present=${result.present} pending=${acceptance.isPending()} saved=${acceptance.isSaved(id)} photoRotation=${result.photoRotation}")
            }
            // Only the processed thumbnail has a quad: never draw stale geometry over live preview.
            val shown = if (showThumbnail) {
            val thumbnailWidth = minOf(360, bitmap.width)
            val thumbnailHeight = maxOf(1, bitmap.height * thumbnailWidth / bitmap.width)
            val scaled = Bitmap.createScaledBitmap(bitmap, thumbnailWidth, thumbnailHeight, true)
            val snapshot = scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (scaled !== bitmap) scaled.recycle()
            if (result.present && result.corners.size == 4) {
                val path = Path()
                result.corners.forEachIndexed { i, c ->
                    if (i == 0) path.moveTo(c.x * snapshot.width, c.y * snapshot.height)
                    else path.lineTo(c.x * snapshot.width, c.y * snapshot.height)
                }
                path.close()
                Canvas(snapshot).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (stable) Color.GREEN else Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 5f
                })
            }
                snapshot
            } else null
            val frameWidth = bitmap.width
            val frameHeight = bitmap.height
            val photo = if (savePhotos && autoSaveEnabled && id != null && !acceptance.isPending() && !acceptance.isSaved(id)) bitmap.copy(Bitmap.Config.ARGB_8888, false) else null
            val timing = getString(R.string.cv_native_timings, result.detectionMs, result.recognitionMs, result.sharpness)
            val label = if (id == null) getString(R.string.cv_native_no_card) else
                getString(if (stable) R.string.cv_native_candidate else R.string.cv_native_checking, id, top!!.score)
            val uiQueued=System.nanoTime()
            runOnUiThread {
                EdScanPerf.record("UI.resultQueue",System.nanoTime()-uiQueued)
                val uiStarted=System.nanoTime()
                try {
                if (!active || token != generation || currentScope!=scopeVersion || isDestroyed) { shown?.recycle(); photo?.recycle(); return@runOnUiThread }
                if(id!=null && top!=null && top.score>=.75f &&
                    result.hits.getOrNull(1)?.let { top.score-it.score>=.08f }==true) prepareVisualCandidate(id)
                if(stable && id!=null && id!=lastUsefulCard) { lastUsefulCard=id; lastUse=SystemClock.elapsedRealtime() }
                if (showThumbnail) {
                    frameView.setImageBitmap(shown)
                    shownBitmap?.recycle(); shownBitmap = shown
                } else shown?.recycle()
                status.text = timing
                displayedId = id
                resultView.text = label + (metadataCache[id]?.let { "\n$it" } ?: "")
                if (!hostProvidesPrices && stable && id != null) loadMetadata(id, token)
                if (autoSaveEnabled) {
                    if (captureGap) acceptance.resetStability()
                    val observation = JSONObject().put("at", System.currentTimeMillis()).put("present", result.present)
                        .put("sharpness", if (result.sharpness.isFinite()) result.sharpness.toDouble() else JSONObject.NULL)
                        .put("frameWidth", frameWidth).put("frameHeight", frameHeight).put("rotation", rotation).put("photoRotation", result.photoRotation)
                        .put("detectionMs", result.detectionMs)
                        .put("recognitionMs", result.recognitionMs).put("captureGap", captureGap)
                        .put("pendingBefore", acceptance.isPending()).put("sameSavedCard", acceptance.isSaved(id))
                        .put("hits", org.json.JSONArray().apply { result.hits.forEach {
                            put(JSONObject().put("id", it.cardId).put("score", if (it.score.isFinite()) it.score.toDouble() else JSONObject.NULL))
                        } }).put("corners", org.json.JSONArray().apply { result.corners.forEach {
                            put(org.json.JSONArray().put(if (it.x.isFinite()) it.x.toDouble() else JSONObject.NULL)
                                .put(if (it.y.isFinite()) it.y.toDouble() else JSONObject.NULL))
                        } })
                    debugFrames.addLast(observation)
                    while (debugFrames.size > 8) debugFrames.removeFirst()
                    if (now - lastEvidenceAt >= 3000) {
                        lastEvidenceAt = now
                        val tuning = EdScanTuning.read(this)
                        EdScanDebug.record(applicationContext, diagnosticKey, "frames", JSONObject()
                            .put("history", org.json.JSONArray(debugFrames.toList()))
                            .put("fastScore", tuning.score.toDouble()).put("fastMargin", tuning.margin.toDouble()).put("fastReadings", tuning.readings)
                            .put("filters", getSharedPreferences("cornelius_scanner", MODE_PRIVATE).getBoolean("safety_filters", true)))
                    }
                    val attempt = acceptance.observe(result.present, result.hits, printingIdentities())
                    if (attempt == null) acceptance.unresolvedMargin(result.present, result.hits)?.let { ambiguity ->
                        requestPrintingIdentities(result.hits.filter { result.hits.first().score - it.score < ambiguity.minimum }.map { it.cardId })
                        resultView.text = getString(R.string.cv_native_ambiguous_margin, ambiguity.margin, ambiguity.minimum) + "\n" + label
                    }
                    if (attempt != null) {
                        val evidence = JSONObject().put("uuid", attempt.cardId).put("score", attempt.score.toDouble())
                            .put("provisionalIds", org.json.JSONArray(attempt.provisionalIds))
                            .put("mode", if (attempt.filtered) "filtered" else "fast")
                            .put("reason", if (attempt.filtered) "consecutive_score_and_margin" else if (attempt.readings == 1) "strong_score_and_margin" else "fast_stable_score_and_margin")
                            .put("readings", attempt.readings).put("minimumScore", attempt.scoreThreshold.toDouble())
                            .put("minimumMargin", attempt.marginThreshold.toDouble()).put("finish", priceFinish)
                            .put("photoEnabled", savePhotos).put("thumbnailEnabled", showThumbnail)
                            .put("engine", CollectorVisionAssets.VERSION)
                            .put("history", org.json.JSONArray(debugFrames.toList()))
                        val debugKey = EdScanDebug.accepted(applicationContext, evidence)
                        acceptedDebug(debugKey)
                        acceptedProvisional(attempt.provisionalIds)
                        if (photo != null && result.corners.size == 4) {
                            val small = Bitmap.createBitmap(160, 224, Bitmap.Config.ARGB_8888)
                            val transform = Matrix()
                            val points = result.corners.flatMap { listOf(it.x * photo.width, it.y * photo.height) }.toFloatArray()
                            if (transform.setPolyToPoly(points, 0, floatArrayOf(0f,0f,160f,0f,160f,224f,0f,224f),0,4)) {
                                Canvas(small).drawBitmap(photo, transform, Paint(Paint.FILTER_BITMAP_FLAG))
                                acceptedPreview(small)
                            } else small.recycle()
                        }
                        acceptedPhoto(if (photo != null) EdScanJobs.capture(applicationContext, photo, result.corners, attempt.cardId, priceFinish) else null)
                        saveReliable(attempt, debugKey)
                    } else photo?.recycle()
                } else photo?.recycle()
                } finally { EdScanPerf.record("UI.resultWork",System.nanoTime()-uiStarted) }
            }
        } catch (e: Exception) {
            candidate = null; repetitions = 0
            runOnUiThread { if (active && token == generation) {
                acceptance.resetStability()
                displayedId = null; resultView.setText(R.string.cv_native_no_card)
                status.text = getString(R.string.cv_native_error, e.message.orEmpty())
            } }
        } finally {
            if (active && token == generation) lastFrameFinishedAt = SystemClock.elapsedRealtime()
            bitmap.recycle()
        }
    }

    private fun saveReliable(attempt: CollectorVisionAcceptanceGate.Attempt, debugKey: String) {
        val recognizedAt = SystemClock.elapsedRealtime()
        playScanTone(ToneGenerator.TONE_PROP_BEEP, 80)
        priceScanKey = debugKey
        priceCardId = attempt.cardId
        recognizedFinish = priceFinish
        recognizedCurrency = priceCurrency
        renderRecognizedPrice()
        if (!hostProvidesPrices) loadMetadata(attempt.cardId, generation)
        android.util.Log.i("CorneliusTiming", "recognized token=${attempt.token}")
        saveStatus.setText(R.string.cv_native_saving)
        val completed = AtomicBoolean(false)
        val finish: (Boolean) -> Unit = { saved ->
            if (completed.compareAndSet(false, true)) runOnUiThread {
                EdScanDebug.record(applicationContext, debugKey, "outcome", JSONObject()
                    .put("saved", saved).put("dataAndSaveMs", SystemClock.elapsedRealtime()-recognizedAt)
                    .put("group", sessionLabel ?: JSONObject.NULL))
                // A save can complete while stopped. Retain its duplicate lock even then.
                if (acceptance.complete(attempt, saved) && !isDestroyed) {
                    val message = if (saved) getString(if (attempt.provisionalIds.isNotEmpty()) R.string.cv_native_saved_provisional else R.string.cv_native_saved) else persistenceFailureMessage ?: getString(R.string.cv_native_save_failed)
                    // The host creates the group lazily on first successful persistence.
                    saveStatus.text = message + (sessionLabel?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
                    if (saved && active) {
                        val elapsed = SystemClock.elapsedRealtime() - recognizedAt
                        android.util.Log.i("CorneliusTiming", "saved token=${attempt.token} dataAndSaveMs=$elapsed")

                    }
                }
            }
        }
        try { persistCandidate(attempt.cardId, attempt.score, finish) }
        catch (_: Exception) { finish(false) }
    }

    private fun playScanTone(kind: Int, durationMs: Int) {
        if (!active || isDestroyed) return
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
            tone?.startTone(kind, durationMs)
        } catch (_: RuntimeException) { /* Audio failure must not affect recognition or persistence. */ }
    }

    // Enrichment never participates in inference and never changes the winning UUID.
    private fun loadMetadata(id: String, token: Int) {
        if ((priceSources[id] == "Scryfall" && metadataCache.containsKey(id)) || !metadataPending.add(id)) return
        if (!id.matches(Regex("[0-9a-fA-F-]{36}"))) { metadataPending.remove(id); return }
        fun readCached(): JSONObject? = runCatching {
            JSONObject(java.io.File(EdScanJobs.directory(applicationContext), "price-$id.json").readText())
                .takeIf { it.optString("id").equals(id, true) }
        }.getOrNull()
        fun publish(data: JSONObject?) {
            if (isDestroyed) return
            metadataPending.remove(id)
            if (data == null) {
                metadataFailed.add(id)
                if (priceCardId == id) priceView.setText(R.string.cv_native_price_unavailable)
            } else {
                metadataFailed.remove(id)
                val label = "${data.optString("name")} · ${data.optString("set").uppercase()} #${data.optString("collector_number")}"
                metadataCache[id] = label
                priceSources[id] = "Scryfall"
                priceCache[id] = data.optJSONObject("prices") ?: JSONObject()
                if (priceCardId == id) renderRecognizedPrice()
                if (active && token == generation && displayedId == id) resultView.text = label
            }
        }
        metadataWorker.execute {
            val cached = readCached()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (cached != null) { publish(cached); return@runOnUiThread }
                val workName = EdScanJobs.price(applicationContext, id)
                val live = androidx.work.WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(workName)
                val observer = object : androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> {
                    override fun onChanged(infos: List<androidx.work.WorkInfo>) {
                        if (infos.none { it.state.isFinished }) return
                        live.removeObserver(this)
                        if (isDestroyed) return
                        metadataWorker.execute { val data=readCached(); runOnUiThread { publish(data) } }
                    }
                }
                live.observe(this, observer)
            }
        }
    }

    private fun renderRecognizedPrice() {
        val id = priceCardId ?: return
        val prices = priceCache[id]
        val label = metadataCache[id].orEmpty()
        if (prices == null) {
            priceView.setText(if (metadataFailed.contains(id)) R.string.cv_native_price_unavailable else R.string.cv_native_price_loading)
            return
        }
        val key = recognizedCurrency + when (recognizedFinish) {
            "foil" -> "_foil"
            "etched" -> "_etched"
            else -> ""
        }
        val amount = prices.optString(key).toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        val value = amount?.let {
            java.text.NumberFormat.getCurrencyInstance().apply {
                currency = java.util.Currency.getInstance(recognizedCurrency.uppercase())
            }.format(it)
        } ?: getString(R.string.cv_native_price_unavailable)
        priceView.text = "$label\n$value · ${priceSources[id] ?: "Scryfall"} · $recognizedFinish"
        val eurKey = "eur" + if (recognizedFinish == "foil") "_foil" else if (recognizedFinish == "etched") "_etched" else ""
        val eur = prices.optString(eurKey).toDoubleOrNull()
        priceScanKey?.let { scan ->
            EdScanDebug.record(applicationContext, scan, "price", JSONObject().put("uuid", id)
                .put("finish", recognizedFinish).put("eur", eur ?: JSONObject.NULL).put("source", priceSources[id] ?: "scryfall"))
            if (active && eur != null) PriceCelebration.show(this, scan, eur)
        }
    }

    override fun onStart() {
        super.onStart(); active=true; lastUse=SystemClock.elapsedRealtime()
        if(EdScanPerf.enabled) { heartbeatExpected=SystemClock.elapsedRealtime()+3000; idleHandler.postDelayed(heartbeat,3000) }
        if(!idlePaused) { EdScanInferencePriority.acquire(this); if(ready) requestCamera(); idleHandler.postDelayed(idleTick,1000) }
    }
    override fun onStop() {
        idleHandler.removeCallbacks(idleTick)
        idleHandler.removeCallbacks(heartbeat)
        scanningStateChanged(false)
        active = false; generation++
        acceptance.resetStability()
        analysis?.clearAnalyzer()
        val bound = listOfNotNull(analysis, preview)
        if (bound.isNotEmpty()) provider?.unbind(*bound.toTypedArray())
        analysis = null; preview = null
        tone?.release(); tone = null
        displayedId = null; resultView.setText(R.string.cv_native_no_card)
        frameView.setImageDrawable(null); shownBitmap?.recycle(); shownBitmap = null
        EdScanInferencePriority.release(this)
        super.onStop()
    }
    override fun onDestroy() {
        EdScanInferencePriority.release(this)
        preparation?.cancel(true)
        worker.execute { engine?.close(); engine = null }
        worker.shutdown(); metadataWorker.shutdown()
        super.onDestroy()
    }
}
