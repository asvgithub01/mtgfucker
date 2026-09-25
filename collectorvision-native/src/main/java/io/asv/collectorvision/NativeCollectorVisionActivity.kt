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
    protected open val autoStartEnabled: Boolean get() = false
    protected open val autoSaveEnabled: Boolean get() = false
    protected open val sessionLabel: String? get() = null
    protected open fun addHostControls(container: LinearLayout) = Unit
    protected open fun acceptedPhoto(key: String?) = Unit
    protected open fun persistCandidate(cardId: String, score: Float, callback: (Boolean) -> Unit) { callback(false) }
    protected open val priceFinish: String get() = "nonfoil"
    protected open val priceCurrency: String get() = "eur"
    private lateinit var priceView: TextView
    @Volatile private var savePhotos = false
    @Volatile private var showThumbnail = true
    private var priceCardId: String? = null
    private var recognizedFinish = "nonfoil"
    private var recognizedCurrency = "eur"
    private val priceCache = mutableMapOf<String, JSONObject>()
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
    private var displayedId: String? = null // UI-confined
    private val metadataCache = mutableMapOf<String, String>() // UI-confined
    private val metadataFailed = mutableSetOf<String>()
    private val metadataPending = mutableSetOf<String>()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) bindCamera() else status.setText(R.string.cv_native_permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12) }
        root.addView(TextView(this).apply { setText(R.string.cv_native_title); textSize = 20f })
        sessionLabel?.let { label -> root.addView(TextView(this).apply { text = label; textSize = 16f }) }
        addHostControls(root)
        if (autoSaveEnabled) {
            val prefs = getSharedPreferences("cornelius_scanner", MODE_PRIVATE)
            val enabled = prefs.getBoolean("safety_filters", true)
            acceptance.setFiltersEnabled(enabled)
            val modeHint = TextView(this).apply {
                textSize = 12f
                setText(if (enabled) R.string.cv_native_filtered_hint else R.string.cv_native_fast_hint)
            }
            root.addView(CheckBox(this).apply {
                tag = "cornelius_safety_filters"
                setText(R.string.cv_native_safety_filters)
                isChecked = enabled
                setOnCheckedChangeListener { _, checked ->
                    acceptance.setFiltersEnabled(checked)
                    prefs.edit().putBoolean("safety_filters", checked).apply()
                    modeHint.setText(if (checked) R.string.cv_native_filtered_hint else R.string.cv_native_fast_hint)
                }
            })
            root.addView(modeHint)
        }
        val controls = LinearLayout(this)
        start = Button(this).apply { setText(R.string.cv_native_start); setOnClickListener { prepare() } }
        // Retain the button only as a recovery action after preparation fails.
        start.visibility = View.GONE
        controls.addView(start, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply { setText(R.string.cv_native_close); setOnClickListener { finish() } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        saveStatus = TextView(this).apply {
            if (autoSaveEnabled) setText(R.string.cv_native_save_ready)
            textSize = 14f
        }
        root.addView(saveStatus)
        resultView = TextView(this).apply { setText(R.string.cv_native_no_card); textSize = 16f }
        root.addView(resultView)
        setContentView(root)
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
        if (!active) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) bindCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun bindCamera() {
        if (!active || !ready || analysis != null) return
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
                status.setText(R.string.cv_native_running)
            } catch (e: Exception) { status.text = getString(R.string.cv_native_error, e.message.orEmpty()) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy, token: Int) {
        var bitmap: Bitmap? = null
        try {
            if (!active || token != generation) return
            val now = SystemClock.elapsedRealtime()
            // Reset for a real capture gap, not for time spent running the models.
            val captureGap = workerGeneration != token || now - lastFrameFinishedAt > 1500
            if (captureGap) { candidate = null; repetitions = 0 }
            workerGeneration = token
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            bitmap = if (rotation == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true).also { if (it !== raw) raw.recycle() }
            val result = engine?.scan(bitmap) ?: return
            val top = result.hits.firstOrNull()
            val id = if (result.present && top != null && top.score >= 0.50f) top.cardId else null
            if (candidate == id && id != null) repetitions++ else { candidate = id; repetitions = if (id == null) 0 else 1 }
            val stable = repetitions >= 2
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
            val photo = if (savePhotos && autoSaveEnabled && id != null) bitmap.copy(Bitmap.Config.ARGB_8888, false) else null
            val timing = getString(R.string.cv_native_timings, result.detectionMs, result.recognitionMs, result.sharpness)
            val label = if (id == null) getString(R.string.cv_native_no_card) else
                getString(if (stable) R.string.cv_native_candidate else R.string.cv_native_checking, id, top!!.score)
            runOnUiThread {
                if (!active || token != generation || isDestroyed) { shown?.recycle(); photo?.recycle(); return@runOnUiThread }
                if (showThumbnail) {
                    frameView.setImageBitmap(shown)
                    shownBitmap?.recycle(); shownBitmap = shown
                } else shown?.recycle()
                status.text = timing
                displayedId = id
                resultView.text = label + (metadataCache[id]?.let { "\n$it" } ?: "")
                if (stable && id != null) loadMetadata(id, token)
                if (autoSaveEnabled) {
                    if (captureGap) acceptance.resetStability()
                    val attempt = acceptance.observe(result.present, result.hits)
                    if (attempt != null) {
                        acceptedPhoto(if (photo != null) EdScanJobs.capture(applicationContext, photo, result.corners, attempt.cardId, priceFinish) else null)
                        saveReliable(attempt)
                    } else photo?.recycle()
                } else photo?.recycle()
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
            bitmap?.recycle(); image.close()
        }
    }

    private fun saveReliable(attempt: CollectorVisionAcceptanceGate.Attempt) {
        val recognizedAt = SystemClock.elapsedRealtime()
        playScanTone(ToneGenerator.TONE_PROP_BEEP, 80)
        priceCardId = attempt.cardId
        recognizedFinish = priceFinish
        recognizedCurrency = priceCurrency
        renderRecognizedPrice()
        loadMetadata(attempt.cardId, generation)
        android.util.Log.i("CorneliusTiming", "recognized token=${attempt.token}")
        saveStatus.setText(R.string.cv_native_saving)
        val completed = AtomicBoolean(false)
        val finish: (Boolean) -> Unit = { saved ->
            if (completed.compareAndSet(false, true)) runOnUiThread {
                // A save can complete while stopped. Retain its duplicate lock even then.
                if (acceptance.complete(attempt, saved) && !isDestroyed) {
                    val message = getString(if (saved) R.string.cv_native_saved else R.string.cv_native_save_failed)
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
        if (metadataCache.containsKey(id) || !metadataPending.add(id)) return
        if (!id.matches(Regex("[0-9a-fA-F-]{36}"))) { metadataPending.remove(id); return }
        val workName = EdScanJobs.price(applicationContext, id)
        val live = androidx.work.WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(workName)
        val observer = object : androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> {
            override fun onChanged(infos: List<androidx.work.WorkInfo>) {
                if (infos.none { it.state.isFinished }) return
                live.removeObserver(this)
                metadataWorker.execute {
                    val data = runCatching {
                        JSONObject(java.io.File(EdScanJobs.directory(applicationContext), "price-$id.json").readText())
                    }.getOrNull()
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        if (data == null) {
                            metadataFailed.add(id)
                            if (priceCardId == id) priceView.setText(R.string.cv_native_price_unavailable)
                        } else {
                            metadataPending.remove(id)
                            val label = "${data.optString("name")} · ${data.optString("set").uppercase()} #${data.optString("collector_number")}"
                            metadataCache[id] = label
                            priceCache[id] = data.optJSONObject("prices") ?: JSONObject()
                            if (priceCardId == id) renderRecognizedPrice()
                            if (active && token == generation && displayedId == id) resultView.append("\n$label")
                        }
                    }
                }
            }
        }
        live.observe(this, observer)
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
        priceView.text = "$label\n$value · Scryfall · $recognizedFinish"
    }

    override fun onStart() { super.onStart(); active = true; if (ready) requestCamera() }
    override fun onStop() {
        active = false; generation++
        acceptance.resetStability()
        analysis?.clearAnalyzer()
        val bound = listOfNotNull(analysis, preview)
        if (bound.isNotEmpty()) provider?.unbind(*bound.toTypedArray())
        analysis = null; preview = null
        tone?.release(); tone = null
        displayedId = null; resultView.setText(R.string.cv_native_no_card)
        frameView.setImageDrawable(null); shownBitmap?.recycle(); shownBitmap = null
        super.onStop()
    }
    override fun onDestroy() {
        preparation?.cancel(true)
        worker.execute { engine?.close(); engine = null }
        worker.shutdown(); metadataWorker.shutdown()
        super.onDestroy()
    }
}
