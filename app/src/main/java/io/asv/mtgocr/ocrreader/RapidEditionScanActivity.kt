package io.asv.mtgocr.ocrreader

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.CardIdentificationResult
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import io.asv.mtgocr.ocrreader.data.LocalCardNameMatch
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.data.SetCardOption
import io.asv.mtgocr.ocrreader.model.CardInfo
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private class RapidPendingCardOcr {
    val startedAtMs = SystemClock.elapsedRealtime()
    var remaining = 4
    var printing: PrintingLineOcrResult? = null
    var title: CardTitleOcrResult? = null
    var nameMatch: LocalCardNameMatch? = null
    var nameCandidates: List<LocalCardNameMatch> = emptyList()
    var language: CardTextLanguageResult? = null
    var visual: RapidVisualEvidence? = null
    var error: Throwable? = null
}

private data class RapidVisualEvidence(
    val jpeg: ByteArray,
    val frame: CardFrameAnalysis
)

private data class RapidRankedEdition(
    val option: CardEditionOption,
    val score: Int,
    val evidence: List<String>
)

private data class RapidSessionEntry(
    var card: CardInfo,
    var scannedCopies: Int = 1
)

private data class RapidAddedCopy(
    val option: CardEditionOption,
    val collectionItemId: String
)

private enum class RapidLiveScanPhase {
    NAME,
    EDGES
}

/** CameraX/OpenCV laboratory kept separate from the maintained scanner. */
class RapidEditionScanActivity : AppCompatActivity() {
    private lateinit var root: View
    private lateinit var preview: PreviewView
    private lateinit var liveGuide: ExperimentalCardGuideView
    private lateinit var correction: CardCropAdjustView
    private lateinit var capture: Button
    private lateinit var cancel: Button
    private lateinit var instruction: TextView
    private lateinit var debug: View
    private lateinit var crop: ImageView
    private lateinit var status: TextView
    private lateinit var loading: View
    private lateinit var loadingText: TextView
    private lateinit var earlyResult: TextView
    private lateinit var sessionButton: Button
    private lateinit var undoLastButton: Button

    private val repository by lazy { CardRepository.get(this) }
    private val printingLineOcr = PrintingLineOcr()
    private val cardTitleOcr = CardTitleOcr()
    private val liveCardNameOcr = LiveCardNameOcr()
    private val cardLanguageDetector = CardTextLanguageDetector()
    private val openCvDetector = OpenCvCardDetector(guideAssisted = true)
    private val stability = AutoCaptureStability(
        requiredFrames = 8,
        requiredMillis = 650L,
        maximumCornerMovement = .014f
    )
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val photoExecutor = Executors.newSingleThreadExecutor()
    private val metadataExecutor = Executors.newSingleThreadExecutor()
    private val captureGate = AtomicBoolean(false)
    private val liveNameOcrGate = AtomicBoolean(false)
    private val liveNameLookupGate = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var debugBitmap: Bitmap? = null
    private var correctionMode = false
    private var analysisInFlight = false
    private var autoAnalysisScheduled = false
    private var earlyLookupGeneration = 0
    private var feedbackSnackbar: Snackbar? = null
    private var toneGenerator: ToneGenerator? = null
    private val sessionEntries = ArrayList<RapidSessionEntry>()
    private val addedCopies = ArrayList<RapidAddedCopy>()
    private val addedCollectionItemIds = LinkedHashSet<String>()
    private var sessionAdapter: RapidSessionAdapter? = null
    private var cameraStarting = false
    private var openCvReady = false
    private var missedFrames = 0
    private var lastAnalyzedAt = 0L
    @Volatile private var liveScanPhase = RapidLiveScanPhase.NAME
    private var recognizedNameMatch: LocalCardNameMatch? = null
    @Volatile private var lastDetected: OpenCvDetectedQuad? = null
    @Volatile private var knownSetCodes: Set<String> = emptySet()
    private val quadHistory = ArrayDeque<Array<PointF>>()
    private var liveDetectionSamples = 0
    private var liveDetectionTotalMs = 0L
    private var liveDetectionMaxMs = 0L
    private var liveDetectionHits = 0
    private val autoAnalyzeRunnable = Runnable {
        autoAnalysisScheduled = false
        if (correctionMode && !analysisInFlight && !isFinishing && !isDestroyed) {
            analyzeCorrectedPhoto()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rapid_edition_scan)
        root = findViewById(R.id.rapidScanRoot)
        preview = findViewById(R.id.experimentalCameraPreview)
        liveGuide = findViewById(R.id.experimentalScanLiveGuide)
        correction = findViewById(R.id.experimentalCropCorrection)
        capture = findViewById(R.id.experimentalScanCapture)
        cancel = findViewById(R.id.experimentalScanCancel)
        instruction = findViewById(R.id.experimentalScanInstruction)
        debug = findViewById(R.id.experimentalScanDebug)
        crop = findViewById(R.id.experimentalScanCrop)
        status = findViewById(R.id.experimentalScanStatus)
        loading = findViewById(R.id.rapidScanLoading)
        loadingText = findViewById(R.id.rapidScanLoadingText)
        earlyResult = findViewById(R.id.rapidScanEarlyResult)
        sessionButton = findViewById(R.id.rapidScanSession)
        undoLastButton = findViewById(R.id.rapidScanUndoLast)
        toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        }.getOrNull()
        correction.onUserInteraction = {
            if (autoAnalysisScheduled) {
                correction.removeCallbacks(autoAnalyzeRunnable)
                autoAnalysisScheduled = false
                instruction.setText(R.string.rapid_scan_adjust_then_confirm)
            }
        }
        sessionButton.setOnClickListener { showRapidSession() }
        undoLastButton.setOnClickListener { confirmUndoLastScan() }
        capture.isEnabled = false

        openCvReady = OpenCVLoader.initLocal()
        if (!openCvReady) {
            capture.isEnabled = false
            showError(getString(R.string.experimental_scan_opencv_error))
        }
        cancel.setOnClickListener {
            if (correctionMode) returnToCamera() else finish()
        }
        capture.setOnClickListener {
            if (correctionMode) {
                analyzeCorrectedPhoto()
            } else if (liveScanPhase == RapidLiveScanPhase.EDGES &&
                captureGate.compareAndSet(false, true)
            ) {
                capturePhoto(lastDetected, automatic = false)
            }
        }
        metadataExecutor.execute {
            knownSetCodes = CardDatabase.get(this).cardDao().magicSets()
                .mapTo(LinkedHashSet()) { it.code.uppercase(Locale.US) }
        }
    }

    private fun ensureCamera() {
        if (!openCvReady || correctionMode || isFinishing || isDestroyed) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), RC_CAMERA)
            return
        }
        if (cameraStarting) return
        cameraStarting = true
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraStarting = false
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || correctionMode) return@addListener
            try {
                bindCamera(providerFuture.get())
            } catch (_: Throwable) {
                showError(getString(R.string.experimental_scan_camera_error))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        provider.unbindAll()
        cameraProvider = provider
        val rotation = preview.display?.rotation ?: Surface.ROTATION_0
        val previewUseCase = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 960))
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val stillCapture = ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        analysis.setAnalyzer(analysisExecutor, ::analyzeLiveFrame)
        previewUseCase.setSurfaceProvider(preview.surfaceProvider)
        val viewPort = preview.viewPort
        val camera = if (viewPort != null) {
            val group = UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .addUseCase(previewUseCase)
                .addUseCase(analysis)
                .addUseCase(stillCapture)
                .build()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group)
        } else {
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                analysis,
                stillCapture
            )
        }
        imageAnalysis = analysis
        imageCapture = stillCapture
        val center = preview.meteringPointFactory.createPoint(.5f, .5f)
        camera.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(
                center,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).setAutoCancelDuration(2, TimeUnit.SECONDS).build()
        )
        capture.isEnabled = liveScanPhase == RapidLiveScanPhase.EDGES
        instruction.setText(
            if (liveScanPhase == RapidLiveScanPhase.NAME) R.string.experimental_scan_live_reading_name
            else R.string.experimental_scan_finding_edges
        )
    }

    private fun analyzeLiveFrame(image: ImageProxy) {
        if (captureGate.get() || correctionMode) {
            image.close()
            return
        }
        if (liveScanPhase == RapidLiveScanPhase.NAME) {
            analyzeLiveName(image)
            return
        }
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzedAt < ANALYSIS_INTERVAL_MS) return
            lastAnalyzedAt = now
            val detectionStartedAt = SystemClock.elapsedRealtime()
            val detected = openCvDetector.detect(image)
            recordLiveDetectionTime(
                SystemClock.elapsedRealtime() - detectionStartedAt,
                detected != null
            )
            if (detected == null) {
                missedFrames++
                if (missedFrames >= MISSES_BEFORE_RESET) {
                    stability.reset()
                    quadHistory.clear()
                    lastDetected = null
                    runOnUiThread {
                        if (!correctionMode) {
                            liveGuide.showDetection(null, 0f)
                            instruction.setText(R.string.experimental_scan_finding_edges)
                        }
                    }
                }
                return
            }
            missedFrames = 0
            val decision = stability.observe(detected.normalizedCorners, detected.confidence, now)
            val consensus = consensusDetection(detected)
            lastDetected = consensus
            runOnUiThread {
                if (!correctionMode) {
                    liveGuide.showDetection(consensus, decision.progress)
                    instruction.setText(
                        if (decision.progress >= .72f) R.string.experimental_scan_hold_steady
                        else R.string.experimental_scan_card_detected
                    )
                }
            }
            if (decision.shouldCapture && captureGate.compareAndSet(false, true)) {
                runOnUiThread {
                    instruction.setText(R.string.experimental_scan_auto_capture)
                    capturePhoto(consensus, automatic = true)
                }
            }
        } catch (_: Throwable) {
            missedFrames++
        } finally {
            image.close()
        }
    }

    private fun analyzeLiveName(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalyzedAt < NAME_ANALYSIS_INTERVAL_MS ||
            !liveNameOcrGate.compareAndSet(false, true)
        ) {
            image.close()
            return
        }
        lastAnalyzedAt = now
        try {
            liveCardNameOcr.recognize(image) { candidates, _ ->
                image.close()
                liveNameOcrGate.set(false)
                if (candidates.isEmpty() || correctionMode ||
                    liveScanPhase != RapidLiveScanPhase.NAME || isFinishing || isDestroyed ||
                    !liveNameLookupGate.compareAndSet(false, true)
                ) {
                    return@recognize
                }
                repository.matchLocalOcrText(candidates) { match ->
                    liveNameLookupGate.set(false)
                    if (match == null || correctionMode || liveScanPhase != RapidLiveScanPhase.NAME ||
                        isFinishing || isDestroyed
                    ) {
                        return@matchLocalOcrText
                    }
                    recognizedNameMatch = match
                    announceNameAndPrice(match)
                    liveScanPhase = RapidLiveScanPhase.EDGES
                    stability.reset()
                    quadHistory.clear()
                    missedFrames = 0
                    lastDetected = null
                    lastAnalyzedAt = 0L
                    liveGuide.showDetection(null, 0f)
                    capture.isEnabled = true
                    instruction.text = getString(
                        R.string.experimental_scan_live_name_found_find_edges,
                        match.displayName
                    )
                }
            }
        } catch (_: Throwable) {
            liveNameOcrGate.set(false)
            image.close()
        }
    }

    private fun recordLiveDetectionTime(durationMs: Long, detected: Boolean) {
        liveDetectionSamples++
        liveDetectionTotalMs += durationMs
        liveDetectionMaxMs = maxOf(liveDetectionMaxMs, durationMs)
        if (detected) liveDetectionHits++
        if (liveDetectionSamples >= LIVE_TIMING_SAMPLE_SIZE) {
            Log.d(
                PERF_TAG,
                "bordes_live media=${liveDetectionTotalMs / liveDetectionSamples}ms " +
                    "max=${liveDetectionMaxMs}ms aciertos=$liveDetectionHits/$liveDetectionSamples"
            )
            liveDetectionSamples = 0
            liveDetectionTotalMs = 0L
            liveDetectionMaxMs = 0L
            liveDetectionHits = 0
        }
    }

    private fun consensusDetection(detected: OpenCvDetectedQuad): OpenCvDetectedQuad {
        val latest = quadHistory.lastOrNull()
        if (latest != null) {
            val jump = latest.indices.maxOf { index ->
                val dx = latest[index].x - detected.normalizedCorners[index].x
                val dy = latest[index].y - detected.normalizedCorners[index].y
                kotlin.math.hypot(dx, dy)
            }
            if (jump > MAX_CONSENSUS_JUMP) quadHistory.clear()
        }
        quadHistory.addLast(detected.normalizedCorners.map { PointF(it.x, it.y) }.toTypedArray())
        while (quadHistory.size > CONSENSUS_FRAMES) quadHistory.removeFirst()
        if (quadHistory.size < 3) return detected
        val corners = Array(4) { index ->
            val xs = quadHistory.map { it[index].x }.sorted()
            val ys = quadHistory.map { it[index].y }.sorted()
            PointF(xs[xs.size / 2], ys[ys.size / 2])
        }
        return detected.copy(normalizedCorners = corners)
    }

    private fun announceNameAndPrice(match: LocalCardNameMatch) {
        val generation = ++earlyLookupGeneration
        val pending = getString(R.string.rapid_scan_name_pending, match.displayName)
        earlyResult.text = pending
        earlyResult.visibility = View.VISIBLE
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = Snackbar.make(root, pending, Snackbar.LENGTH_SHORT).also { it.show() }
        repository.quickScanCard(match.canonicalName) { option, _ ->
            if (generation != earlyLookupGeneration || isFinishing || isDestroyed) {
                return@quickScanCard
            }
            val price = option?.price?.let { amount ->
                PriceCurrency.format(this, amount, option.currency ?: PriceCurrency.EUR)
            }
            val message = if (price.isNullOrBlank()) {
                getString(R.string.rapid_scan_name_found, match.displayName)
            } else {
                getString(R.string.rapid_scan_early_price, match.displayName, price)
            }
            earlyResult.text = message
            feedbackSnackbar?.dismiss()
            feedbackSnackbar = Snackbar.make(root, message, Snackbar.LENGTH_SHORT).also { it.show() }
        }
    }

    private fun announceVerified(displayName: String, candidates: List<RapidRankedEdition>) {
        val option = candidates.firstOrNull()?.option
        val price = option?.price?.let { amount ->
            PriceCurrency.format(this, amount, option.currency ?: PriceCurrency.EUR)
        }
        earlyResult.text = if (option == null) {
            getString(R.string.rapid_scan_name_found, displayName)
        } else {
            getString(
                R.string.rapid_scan_verified,
                displayName,
                option.setCode.uppercase(Locale.US),
                price ?: getString(R.string.no_price)
            )
        }
        earlyResult.visibility = View.VISIBLE
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 140)
    }

    private fun showLoading(message: CharSequence) {
        loadingText.text = message
        loading.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loading.visibility = View.GONE
    }

    private fun capturePhoto(detected: OpenCvDetectedQuad?, automatic: Boolean) {
        val stillCapture = imageCapture
        if (stillCapture == null) {
            captureGate.set(false)
            return
        }
        capture.isEnabled = false
        showLoading(getString(R.string.rapid_scan_loading_capture))
        instruction.setText(
            if (automatic) R.string.experimental_scan_auto_capture
            else R.string.edition_scan_freezing_photo
        )
        debug.visibility = View.GONE
        replaceDebugBitmap(null)
        val output = File.createTempFile("experimental-card-", ".jpg", cacheDir)
        val captureStartedAt = SystemClock.elapsedRealtime()
        stillCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(output).build(),
            photoExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    Log.d(PERF_TAG, "captura_jpeg=${SystemClock.elapsedRealtime() - captureStartedAt}ms")
                    val decodeStartedAt = SystemClock.elapsedRealtime()
                    val bitmap = decodePhoto(output)
                    output.delete()
                    Log.d(PERF_TAG, "decodificacion_jpeg=${SystemClock.elapsedRealtime() - decodeStartedAt}ms")
                    if (bitmap == null) {
                        cameraFailure()
                        return
                    }
                    // The saved JPEG has more detail than the live YUV frame. Always redetect on
                    // that final image instead of blindly scaling the last preview quadrilateral.
                    val stillDetectionStartedAt = SystemClock.elapsedRealtime()
                    val stillDetected = openCvDetector.detect(bitmap)
                    Log.d(
                        PERF_TAG,
                        "bordes_foto=${SystemClock.elapsedRealtime() - stillDetectionStartedAt}ms"
                    )
                    val corners = captureCorners(bitmap, stillDetected, detected)
                        ?: CardQuadrilateralDetector.detect(bitmap)?.corners
                    runOnUiThread {
                        if (isFinishing || isDestroyed) {
                            bitmap.recycle()
                            return@runOnUiThread
                        }
                        if (corners == null) {
                            bitmap.recycle()
                            cameraFailure(getString(R.string.experimental_scan_card_not_found))
                        } else {
                            showCapturedPhoto(bitmap, corners, automatic)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    output.delete()
                    cameraFailure()
                }
            }
        )
    }

    private fun captureCorners(
        bitmap: Bitmap,
        still: OpenCvDetectedQuad?,
        live: OpenCvDetectedQuad?
    ): Array<PointF>? {
        if (still == null) return live?.cornersFor(bitmap.width, bitmap.height)
        val stillCorners = still.normalizedCorners
        val liveCorners = live?.normalizedCorners
        val normalized = if (liveCorners != null && liveCorners.size == 4) {
            val disagreement = stillCorners.indices.maxOf { index ->
                kotlin.math.hypot(
                    stillCorners[index].x - liveCorners[index].x,
                    stillCorners[index].y - liveCorners[index].y
                )
            }
            if (disagreement <= MAX_STILL_LIVE_DISAGREEMENT) {
                Array(4) { index ->
                    PointF(
                        stillCorners[index].x * STILL_WEIGHT +
                            liveCorners[index].x * (1f - STILL_WEIGHT),
                        stillCorners[index].y * STILL_WEIGHT +
                            liveCorners[index].y * (1f - STILL_WEIGHT)
                    )
                }
            } else stillCorners
        } else stillCorners
        return normalized.map { PointF(it.x * bitmap.width, it.y * bitmap.height) }.toTypedArray()
    }

    private fun showCapturedPhoto(bitmap: Bitmap, corners: Array<PointF>, automatic: Boolean) {
        hideLoading()
        cameraProvider?.unbindAll()
        imageAnalysis = null
        imageCapture = null
        correction.setPhoto(bitmap, corners)
        correction.visibility = View.VISIBLE
        liveGuide.visibility = View.GONE
        correctionMode = true
        cancel.setText(R.string.edition_scan_retake_photo)
        capture.setText(R.string.experimental_scan_read_printing)
        capture.isEnabled = true
        instruction.setText(
            if (automatic) R.string.experimental_scan_auto_analyzing
            else R.string.edition_scan_adjust_corners_auto
        )
        if (automatic) {
            autoAnalysisScheduled = true
            correction.postDelayed(autoAnalyzeRunnable, AUTO_ANALYZE_DELAY_MS)
        }
    }

    private fun cameraFailure(message: String = getString(R.string.experimental_scan_camera_error)) {
        runOnUiThread {
            hideLoading()
            captureGate.set(false)
            stability.reset()
            quadHistory.clear()
            capture.isEnabled = liveScanPhase == RapidLiveScanPhase.EDGES
            instruction.setText(
                if (liveScanPhase == RapidLiveScanPhase.NAME) R.string.experimental_scan_live_reading_name
                else R.string.experimental_scan_finding_edges
            )
            showError(message)
        }
    }

    private fun analyzeCorrectedPhoto() {
        if (analysisInFlight) return
        correction.removeCallbacks(autoAnalyzeRunnable)
        autoAnalysisScheduled = false
        val corrected = correction.extractCardBitmap() ?: run {
            instruction.setText(R.string.edition_scan_invalid_crop)
            return
        }
        analysisInFlight = true
        capture.isEnabled = false
        debug.visibility = View.VISIBLE
        status.setText(R.string.experimental_scan_reading_name_first)
        instruction.setText(R.string.experimental_scan_reading_name_first)
        showLoading(getString(R.string.rapid_scan_loading_ocr))
        replaceDebugBitmap(null)
        val pending = RapidPendingCardOcr().apply { nameMatch = recognizedNameMatch }
        fun completeIfReady(): Boolean = synchronized(pending) {
            pending.remaining--
            pending.remaining == 0
        }
        val titleStartedAt = SystemClock.elapsedRealtime()
        cardTitleOcr.recognize(corrected) { result, error ->
            Log.d(PERF_TAG, "ocr_nombre=${SystemClock.elapsedRealtime() - titleStartedAt}ms")
            synchronized(pending) {
                pending.title = result
                if (error != null && pending.error == null) pending.error = error
            }
            if (result == null || result.lines.isEmpty()) {
                if (completeIfReady()) finishCombinedOcr(corrected, pending)
            } else {
                val nameLookupStartedAt = SystemClock.elapsedRealtime()
                repository.matchLocalPhotoText(result.lines) { matches ->
                    Log.d(
                        PERF_TAG,
                        "busqueda_nombre=${SystemClock.elapsedRealtime() - nameLookupStartedAt}ms"
                    )
                    val staticMatches = matches.map {
                        LocalCardNameMatch(it.canonicalName, it.displayName, it.language)
                    }.distinctBy { it.canonicalName.lowercase(Locale.ROOT) }
                    val selected = synchronized(pending) {
                        val live = pending.nameMatch
                        pending.nameCandidates = (staticMatches + listOfNotNull(live))
                            .distinctBy { it.canonicalName.lowercase(Locale.ROOT) }
                        staticMatches.firstOrNull { candidate ->
                            live != null && candidate.canonicalName.equals(
                                live.canonicalName,
                                ignoreCase = true
                            )
                        } ?: staticMatches.firstOrNull() ?: live
                    }
                    synchronized(pending) { pending.nameMatch = selected }
                    if (selected != null) runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            status.text = getString(
                                R.string.experimental_scan_name_found_analyzing_edition,
                                selected.displayName
                            )
                        }
                    }
                    if (completeIfReady()) finishCombinedOcr(corrected, pending)
                }
            }
        }
        val printingStartedAt = SystemClock.elapsedRealtime()
        printingLineOcr.recognize(corrected) { result, error ->
            Log.d(PERF_TAG, "ocr_impresion=${SystemClock.elapsedRealtime() - printingStartedAt}ms")
            synchronized(pending) {
                pending.printing = result
                if (error != null && pending.error == null) pending.error = error
            }
            if (completeIfReady()) finishCombinedOcr(corrected, pending)
            val languageStartedAt = SystemClock.elapsedRealtime()
            // PrintingLineOcr already reads the whole card to recover the year on old cards.
            // Reuse that text instead of launching an additional OCR pass over the rules box.
            cardLanguageDetector.detectText(result?.fullCardText.orEmpty(), "") { language ->
                Log.d(
                    PERF_TAG,
                    "idioma_sin_ocr_extra=${SystemClock.elapsedRealtime() - languageStartedAt}ms"
                )
                synchronized(pending) { pending.language = language }
                if (completeIfReady()) finishCombinedOcr(corrected, pending)
            }
        }
        val visualStartedAt = SystemClock.elapsedRealtime()
        photoExecutor.execute {
            val evidence = runCatching {
                val frame = CardFrameAnalyzer.analyzeTightCard(corrected)
                val jpeg = ByteArrayOutputStream().use { output ->
                    corrected.compress(Bitmap.CompressFormat.JPEG, 94, output)
                    output.toByteArray()
                }
                RapidVisualEvidence(jpeg, frame)
            }
            synchronized(pending) {
                pending.visual = evidence.getOrNull()
                evidence.exceptionOrNull()?.let { if (pending.error == null) pending.error = it }
            }
            Log.d(PERF_TAG, "analisis_visual=${SystemClock.elapsedRealtime() - visualStartedAt}ms")
            if (completeIfReady()) finishCombinedOcr(corrected, pending)
        }
    }

    private fun finishCombinedOcr(card: Bitmap, pending: RapidPendingCardOcr) {
        Log.d(PERF_TAG, "analisis_total=${SystemClock.elapsedRealtime() - pending.startedAtMs}ms")
        card.recycle()
        val printing = synchronized(pending) { pending.printing }
        val title = synchronized(pending) { pending.title }
        val nameMatch = synchronized(pending) { pending.nameMatch }
        val nameCandidates = synchronized(pending) { pending.nameCandidates }
        val language = synchronized(pending) { pending.language }
        val visual = synchronized(pending) { pending.visual }
        val error = synchronized(pending) { pending.error }
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                printing?.preview?.recycle()
                return@runOnUiThread
            }
            if (printing == null && title == null) {
                finishAnalysis()
                showError(error?.message ?: getString(R.string.experimental_scan_ocr_error))
                return@runOnUiThread
            }
            printing?.preview?.let(::replaceDebugBitmap)
            val guess = PrintingMetadataParser.parse(printing?.rawText.orEmpty(), knownSetCodes)
            resolveIdentity(printing, title, nameMatch, nameCandidates, language, visual, guess)
        }
    }

    private fun resolveIdentity(
        printing: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        match: LocalCardNameMatch?,
        possibleNames: List<LocalCardNameMatch>,
        language: CardTextLanguageResult?,
        visual: RapidVisualEvidence?,
        guess: PrintingMetadataGuess
    ) {
        val allNameMatches = (possibleNames + listOfNotNull(match))
            .distinctBy { it.canonicalName.lowercase(Locale.ROOT) }
        val preferredMatch = allNameMatches.firstOrNull() ?: match
        if (preferredMatch == null) status.setText(R.string.experimental_scan_resolving_name)
        val setCodes = guess.setCodeCandidates.asSequence()
            .filter { knownSetCodes.isEmpty() || it in knownSetCodes }
            .take(MAX_SET_CANDIDATES)
            .toList()
        val canResolvePrinting = setCodes.isNotEmpty() && guess.collectorNumber != null
        if (canResolvePrinting) {
            status.setText(R.string.experimental_scan_resolving_printing)
            showLoading(getString(R.string.rapid_scan_loading_printing))
            repository.resolvePrintingMetadata(setCodes, guess.collectorNumber!!) { cards, error ->
                if (isFinishing || isDestroyed) return@resolvePrintingMetadata
                val exactMatches = cards.distinctBy {
                    Triple(it.cardName, it.setCode, it.collectorNumber)
                }
                val exactNames = exactMatches.distinctBy { it.cardName.lowercase(Locale.ROOT) }
                val matchedPair = allNameMatches.firstNotNullOfOrNull { ocrMatch ->
                    exactNames.firstOrNull {
                        it.cardName.equals(ocrMatch.canonicalName, ignoreCase = true) ||
                            it.cardName.equals(ocrMatch.displayName, ignoreCase = true)
                    }?.let { it to ocrMatch }
                }
                val matchingOcrName = matchedPair?.first
                val matchingOcrEvidence = matchedPair?.second
                val resolvedPrinting = matchingOcrName ?: exactNames.singleOrNull()
                if (resolvedPrinting != null) {
                    val displayName = matchingOcrEvidence?.displayName
                        ?.takeIf { matchingOcrName != null }
                        ?: resolvedPrinting.cardName
                    loadEditionCandidates(
                        resolvedPrinting.cardName,
                        displayName,
                        printing,
                        title,
                        effectiveLanguage(
                            guess,
                            language,
                            matchingOcrEvidence?.language.orEmpty(),
                            titleEvidence(title, displayName)
                        ),
                        language,
                        visual,
                        guess,
                        exactMatches
                    )
                } else if (preferredMatch != null) {
                    loadEditionCandidates(
                        preferredMatch.canonicalName,
                        preferredMatch.displayName,
                        printing,
                        title,
                        effectiveLanguage(
                            guess,
                            language,
                            preferredMatch.language,
                            titleEvidence(title, preferredMatch.displayName)
                        ),
                        language,
                        visual,
                        guess,
                        exactMatches
                    )
                } else {
                    finishAnalysis()
                    showUnresolvedResult(printing, title, language, visual, guess, exactMatches, error)
                }
            }
        } else if (preferredMatch != null) {
            loadEditionCandidates(
                preferredMatch.canonicalName,
                preferredMatch.displayName,
                printing,
                title,
                effectiveLanguage(
                    guess,
                    language,
                    preferredMatch.language,
                    titleEvidence(title, preferredMatch.displayName)
                ),
                language,
                visual,
                guess,
                emptyList()
            )
        } else {
            finishAnalysis()
            showUnresolvedResult(printing, title, language, visual, guess, emptyList(), null)
        }
    }

    private fun loadEditionCandidates(
        canonicalName: String,
        displayName: String,
        printing: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        detectedLanguage: String,
        languageEvidence: CardTextLanguageResult?,
        visual: RapidVisualEvidence?,
        guess: PrintingMetadataGuess,
        exactMatches: List<SetCardOption>
    ) {
        val languageCode = detectedLanguage.trim().lowercase(Locale.US)
        if (visual == null) {
            loadMetadataEditionCandidates(
                canonicalName,
                displayName,
                printing,
                title,
                languageCode,
                languageEvidence,
                guess,
                exactMatches
            )
            return
        }
        status.text = getString(R.string.experimental_scan_comparing_visual_language, displayName)
        showLoading(getString(R.string.rapid_scan_loading_visual, displayName))
        val detectedSets = guess.setCodeCandidates.asSequence()
            .map { it.uppercase(Locale.US) }
            .filter { knownSetCodes.isEmpty() || it in knownSetCodes }
            .take(MAX_SET_CANDIDATES)
            .toSet()
        val compareChronicles = ChroniclesSymbolPolicy.applies(
            detectedSets,
            guess.printingYear,
            visual.frame.borderColor
        )
        val visualSetCodes = ChroniclesSymbolPolicy.visualSetCodes(
            detectedSets,
            guess.printingYear,
            visual.frame.borderColor
        )
        repository.identifyCardArtwork(
            cardName = canonicalName,
            // A false language guess must not remove Chronicles before its retained old symbol
            // and white border can be compared. The accepted scan still keeps its OCR language.
            languageCode = if (compareChronicles) "" else languageCode,
            jpeg = visual.jpeg,
            lockedSetCodes = visualSetCodes,
            preferFoil = false,
            alreadyCropped = true,
            useShapeSymbolMatcher = true
        ) { result, error ->
            if (isFinishing || isDestroyed) {
                recycleIdentificationBitmaps(result)
                return@identifyCardArtwork
            }
            if (result.candidates.isNotEmpty()) {
                val displayed = result.analysisPreview ?: result.setSymbolCrop
                displayed?.let(::replaceDebugBitmap)
                recycleIdentificationBitmaps(result, displayed)
                val ranked = rankVisualEditions(result, guess, languageCode)
                finishAnalysis()
                showEditionCandidates(
                    displayName,
                    ranked,
                    printing,
                    title,
                    languageEvidence,
                    languageCode,
                    result,
                    visual.frame,
                    guess
                )
            } else {
                recycleIdentificationBitmaps(result)
                if (error == null && languageCode.isNotBlank() && result.languageFilteredOut > 0) {
                    // A wrong/overconfident language guess must not leave the user without any
                    // editions. Fall back to the complete name-based list and keep OCR evidence.
                    loadMetadataEditionCandidates(
                        canonicalName,
                        displayName,
                        printing,
                        title,
                        "",
                        languageEvidence,
                        guess,
                        exactMatches,
                        null
                    )
                } else {
                    loadMetadataEditionCandidates(
                        canonicalName,
                        displayName,
                        printing,
                        title,
                        languageCode,
                        languageEvidence,
                        guess,
                        exactMatches,
                        error
                    )
                }
            }
        }
    }

    private fun effectiveLanguage(
        guess: PrintingMetadataGuess,
        detected: CardTextLanguageResult?,
        titleLanguage: String = "",
        titleText: String = ""
    ): String {
        val titleHint = ScanLanguagePolicy.localizedTitleLanguage(titleText)
        return CardLanguageEvidenceResolver.resolve(
            footerLanguage = guess.languageCode,
            detectedRulesLanguage = detected?.languageCode,
            detectedRulesConfidence = detected?.confidence ?: 0f,
            matchedTitleLanguage = titleHint ?: titleLanguage
        )
    }

    private fun titleEvidence(title: CardTitleOcrResult?, displayName: String): String =
        (listOf(displayName) + title?.lines.orEmpty()).joinToString(" ")

    private fun loadMetadataEditionCandidates(
        canonicalName: String,
        displayName: String,
        printing: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        languageCode: String,
        languageEvidence: CardTextLanguageResult?,
        guess: PrintingMetadataGuess,
        exactMatches: List<SetCardOption>,
        visualError: Throwable? = null
    ) {
        status.text = getString(R.string.experimental_scan_loading_editions, displayName)
        showLoading(getString(R.string.rapid_scan_loading_prices, displayName))
        var delivered = false
        repository.loadCard(
            canonicalName,
            forcePriceRefresh = false,
            deliverEditionsBeforePrices = true
        ) { options, error ->
            if (isFinishing || isDestroyed || delivered) return@loadCard
            if (options.isNotEmpty()) {
                delivered = true
                repository.localizedEditionOptions(canonicalName, languageCode, options) { localized, languageError ->
                    if (isFinishing || isDestroyed) return@localizedEditionOptions
                    val candidates = localized.ifEmpty { options }
                    if (candidates.isNotEmpty()) {
                        val ranked = rankEditions(candidates, guess, languageCode)
                        finishAnalysis()
                        showEditionCandidates(
                            displayName,
                            ranked,
                            printing,
                            title,
                            languageEvidence,
                            languageCode,
                            null,
                            null,
                            guess
                        )
                    } else {
                        finishAnalysis()
                        val finalError = languageError ?: visualError ?: if (languageCode.isNotBlank()) {
                            IllegalStateException(
                                getString(R.string.experimental_scan_no_language_printings, languageCode.uppercase(Locale.US))
                            )
                        } else null
                        showUnresolvedResult(
                            printing,
                            title,
                            languageEvidence,
                            null,
                            guess,
                            exactMatches,
                            finalError,
                            displayName
                        )
                    }
                }
            } else if (error != null) {
                delivered = true
                finishAnalysis()
                showUnresolvedResult(
                    printing,
                    title,
                    languageEvidence,
                    null,
                    guess,
                    exactMatches,
                    error,
                    displayName
                )
            }
        }
    }

    private fun rankEditions(
        options: List<CardEditionOption>,
        guess: PrintingMetadataGuess,
        languageCode: String = "",
        maxResults: Int = MAX_EDITION_RESULTS
    ): List<RapidRankedEdition> {
        val setCandidates = guess.setCodeCandidates.map { it.uppercase(Locale.US) }
        return options.groupBy(CardEditionOption::printingUuid)
            .values
            .map { finishes -> finishes.firstOrNull { !it.isFoil } ?: finishes.first() }
            .map { option ->
                val evidence = ArrayList<String>()
                var score = 0
                if (languageCode.isNotBlank()) {
                    score += 55
                    evidence += getString(
                        R.string.experimental_scan_evidence_language,
                        languageCode.uppercase(Locale.US)
                    )
                }
                val setIndex = setCandidates.indexOf(option.setCode.uppercase(Locale.US))
                if (setIndex >= 0) {
                    score += 100 - setIndex.coerceAtMost(10) * 3
                    evidence += getString(R.string.experimental_scan_evidence_set)
                }
                if (guess.collectorNumber != null && PrintingMetadataParser.collectorKeysMatch(
                        option.collectorNumber,
                        guess.collectorNumber
                    )
                ) {
                    score += 85
                    evidence += getString(R.string.experimental_scan_evidence_collector)
                }
                if (guess.printingYear != null && option.releaseDate.take(4).toIntOrNull() == guess.printingYear) {
                    score += 35
                    evidence += getString(R.string.experimental_scan_evidence_year)
                }
                RapidRankedEdition(
                    option,
                    score,
                    evidence.ifEmpty { listOf(getString(R.string.experimental_scan_evidence_name_only)) }
                )
            }
            .sortedWith(compareByDescending<RapidRankedEdition> { it.score }
                .thenByDescending { it.option.releaseDate })
            .let { ranked -> if (maxResults == Int.MAX_VALUE) ranked else ranked.take(maxResults) }
    }

    private fun rankVisualEditions(
        result: CardIdentificationResult,
        guess: PrintingMetadataGuess,
        languageCode: String
    ): List<RapidRankedEdition> {
        val setCandidates = guess.setCodeCandidates.map { it.uppercase(Locale.US) }
        val chroniclesSymbolCase = result.detectedBorder == CardBorderColor.WHITE &&
            ChroniclesSymbolPolicy.hasRetainedSymbolEvidence(setCandidates)
        return result.candidates
            .distinctBy { it.option.printingUuid }
            .map { candidate ->
                val option = candidate.option
                val evidence = ArrayList<String>()
                var score = ((1.0 - candidate.distance.coerceIn(0.0, 1.0)) * 120).toInt()
                evidence += getString(
                    R.string.experimental_scan_evidence_visual,
                    ((1.0 - candidate.distance.coerceIn(0.0, 1.0)) * 100).toInt()
                )
                candidate.setSymbolShapeDistance?.takeIf { result.shapeMatchReliable }?.let { distance ->
                    score += ((1.0 - distance.coerceIn(0.0, 1.0)) * 35).toInt()
                    evidence += getString(
                        R.string.experimental_scan_evidence_symbol_shape,
                        ((1.0 - distance.coerceIn(0.0, 1.0)) * 100).toInt()
                    )
                }
                if (languageCode.isNotBlank()) {
                    score += 55
                    evidence += getString(
                        R.string.experimental_scan_evidence_language,
                        languageCode.uppercase(Locale.US)
                    )
                }
                when {
                    result.detectedBorder == CardBorderColor.MIXED ||
                        result.detectedBorder == CardBorderColor.UNKNOWN -> Unit
                    candidate.borderMatches == true -> {
                        score += 45
                        evidence += getString(
                            R.string.experimental_scan_evidence_border_match,
                            borderLabel(result.detectedBorder)
                        )
                    }
                    candidate.borderMatches == false -> {
                        score -= 45
                        evidence += getString(R.string.experimental_scan_evidence_border_mismatch)
                    }
                    else -> Unit
                }
                val setIndex = setCandidates.indexOf(option.setCode.uppercase(Locale.US))
                if (chroniclesSymbolCase && ChroniclesSymbolPolicy.isChronicles(option.setCode)) {
                    score += 125
                    evidence += getString(R.string.experimental_scan_evidence_chronicles)
                } else if (
                    setIndex >= 0 &&
                    !(chroniclesSymbolCase && ChroniclesSymbolPolicy.isRetainedSymbolSet(option.setCode))
                ) {
                    score += 100 - setIndex.coerceAtMost(10) * 3
                    evidence += getString(R.string.experimental_scan_evidence_set)
                }
                if (guess.collectorNumber != null && PrintingMetadataParser.collectorKeysMatch(
                        option.collectorNumber,
                        guess.collectorNumber
                    )
                ) {
                    score += 85
                    evidence += getString(R.string.experimental_scan_evidence_collector)
                }
                if (guess.printingYear != null && option.releaseDate.take(4).toIntOrNull() == guess.printingYear) {
                    score += 35
                    evidence += getString(R.string.experimental_scan_evidence_year)
                }
                RapidRankedEdition(option, score, evidence)
            }
            .sortedWith(compareByDescending<RapidRankedEdition> { it.score }
                .thenByDescending { it.option.releaseDate })
            .take(MAX_EDITION_RESULTS)
    }

    private fun recycleIdentificationBitmaps(
        result: CardIdentificationResult,
        keep: Bitmap? = null
    ) {
        result.analysisPreview?.takeIf { it !== keep && !it.isRecycled }?.recycle()
        result.setSymbolCrop?.takeIf { it !== keep && !it.isRecycled }?.recycle()
    }

    private fun showEditionCandidates(
        displayName: String,
        candidates: List<RapidRankedEdition>,
        printing: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        language: CardTextLanguageResult?,
        effectiveLanguage: String,
        visualResult: CardIdentificationResult?,
        fallbackFrame: CardFrameAnalysis?,
        guess: PrintingMetadataGuess,
        allowAllEditions: Boolean = true,
        announce: Boolean = true
    ) {
        val summary = resultSummary(
            displayName, printing, title, language, effectiveLanguage, visualResult, fallbackFrame, guess
        )
        if (announce) announceVerified(displayName, candidates)
        status.text = summary
        instruction.setText(R.string.experimental_scan_identified)
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.experimental_scan_editions_title, displayName))
            .setAdapter(ExperimentalEditionAdapter(candidates)) { _, which ->
                addSelectedEdition(candidates[which].option, effectiveLanguage)
            }
            .setNeutralButton(R.string.experimental_scan_show_ocr) { _, _ ->
                showOcrDetails(
                    displayName, candidates, printing, title,
                    language, effectiveLanguage, visualResult, fallbackFrame, guess
                )
            }
            .setNegativeButton(R.string.rapid_scan_scan_another_card) { _, _ -> returnToCamera() }
        if (allowAllEditions) {
            builder.setPositiveButton(R.string.rapid_scan_all_editions) { _, _ ->
                loadAllEditionCandidates(
                    displayName, candidates, printing, title, language, effectiveLanguage,
                    visualResult, fallbackFrame, guess
                )
            }
        }
        builder.show()
    }

    private fun loadAllEditionCandidates(
        displayName: String,
        scannerCandidates: List<RapidRankedEdition>,
        printing: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        language: CardTextLanguageResult?,
        effectiveLanguage: String,
        visualResult: CardIdentificationResult?,
        fallbackFrame: CardFrameAnalysis?,
        guess: PrintingMetadataGuess
    ) {
        val canonicalName = scannerCandidates.firstOrNull()?.option?.cardName ?: displayName
        status.setText(R.string.rapid_scan_loading_all_editions)
        showLoading(getString(R.string.rapid_scan_loading_all_editions))
        var delivered = false
        repository.loadCard(
            canonicalName,
            forcePriceRefresh = false,
            deliverEditionsBeforePrices = true
        ) { options, error ->
            if (isFinishing || isDestroyed || delivered) return@loadCard
            if (options.isNotEmpty()) {
                delivered = true
                val scannerIds = scannerCandidates.mapTo(HashSet()) { it.option.printingUuid }
                val manual = rankEditions(options, guess, effectiveLanguage, Int.MAX_VALUE)
                    .filterNot { it.option.printingUuid in scannerIds }
                    .map { candidate ->
                        candidate.copy(
                            evidence = listOf(getString(R.string.rapid_scan_manual_edition)) +
                                candidate.evidence
                        )
                    }
                hideLoading()
                showEditionCandidates(
                    displayName,
                    (scannerCandidates + manual).distinctBy { it.option.printingUuid },
                    printing,
                    title,
                    language,
                    effectiveLanguage,
                    visualResult,
                    fallbackFrame,
                    guess,
                    allowAllEditions = false,
                    announce = false
                )
            } else if (error != null) {
                delivered = true
                hideLoading()
                Toast.makeText(
                    this,
                    error.message ?: getString(R.string.rapid_scan_all_editions_error),
                    Toast.LENGTH_LONG
                ).show()
                showEditionCandidates(
                    displayName, scannerCandidates, printing, title, language, effectiveLanguage,
                    visualResult, fallbackFrame, guess, announce = false
                )
            }
        }
    }

    private fun addSelectedEdition(option: CardEditionOption, languageCode: String) {
        instruction.setText(R.string.experimental_scan_saving_card)
        capture.isEnabled = false
        metadataExecutor.execute {
            val added = runCatching {
                LegacyCollectionStore.addCopy(this, option, languageCode)
            }
            val card = added.getOrNull()
            if (card != null) {
                repository.selectEdition(card.collectionItemId, option) {}
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (card == null) {
                    capture.isEnabled = true
                    instruction.setText(R.string.experimental_scan_identified)
                    Toast.makeText(
                        this,
                        added.exceptionOrNull()?.message ?: getString(R.string.copy_add_error),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    rememberRapidSession(card, option)
                    returnToCamera()
                    showCardAddedNotification(option, card)
                }
            }
        }
    }

    private fun rememberRapidSession(card: CardInfo, option: CardEditionOption) {
        val existing = sessionEntries.firstOrNull {
            it.card.collectionItemId == card.collectionItemId
        }
        if (existing == null) {
            sessionEntries += RapidSessionEntry(card)
        } else {
            existing.card = card
            existing.scannedCopies++
        }
        addedCopies += RapidAddedCopy(option, card.collectionItemId)
        addedCollectionItemIds += card.collectionItemId
        updateSessionControls()
        sessionAdapter?.notifyDataSetChanged()
    }

    private fun updateSessionControls() {
        setResult(
            RESULT_OK,
            Intent().putStringArrayListExtra(
                EXTRA_SESSION_CARD_IDS,
                ArrayList(addedCollectionItemIds)
            )
        )
        val count = sessionEntries.sumOf(RapidSessionEntry::scannedCopies)
        sessionButton.text = getString(R.string.rapid_scan_session_count, count)
        sessionButton.visibility = if (count > 0) View.VISIBLE else View.GONE
        undoLastButton.visibility = if (addedCopies.isNotEmpty()) View.VISIBLE else View.GONE
        undoLastButton.isEnabled = addedCopies.isNotEmpty()
    }

    private fun confirmUndoLastScan() {
        if (analysisInFlight || correctionMode) return
        val last = addedCopies.lastOrNull() ?: return
        captureGate.set(true)
        AlertDialog.Builder(this)
            .setTitle(R.string.rapid_scan_undo_last)
            .setMessage(getString(R.string.rapid_scan_undo_confirm, last.option.displayName))
            .setPositiveButton(R.string.rapid_scan_undo_last) { _, _ -> undoLastScan(last) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> captureGate.set(false) }
            .setOnCancelListener { captureGate.set(false) }
            .show()
    }

    private fun undoLastScan(last: RapidAddedCopy) {
        undoLastButton.isEnabled = false
        metadataExecutor.execute {
            val removed = runCatching {
                LegacyCollectionStore.removeCopy(this, last.option, last.collectionItemId)
            }.getOrNull()
            if (removed == null) {
                runOnUiThread { showUndoFailure() }
            } else if (removed.remainingQuantity == 0) {
                repository.clearSelectedEdition(last.collectionItemId) {
                    applyUndoResult(last, removed.remainingQuantity)
                }
            } else {
                runOnUiThread { applyUndoResult(last, removed.remainingQuantity) }
            }
        }
    }

    private fun applyUndoResult(last: RapidAddedCopy, remainingQuantity: Int) {
        if (isFinishing || isDestroyed) return
        if (addedCopies.lastOrNull() == last) addedCopies.removeAt(addedCopies.lastIndex)
        val entry = sessionEntries.firstOrNull {
            it.card.collectionItemId == last.collectionItemId
        }
        if (entry != null) {
            entry.scannedCopies--
            entry.card.quantityCount = remainingQuantity
            if (entry.scannedCopies <= 0) sessionEntries.remove(entry)
        }
        if (sessionEntries.none { it.card.collectionItemId == last.collectionItemId }) {
            addedCollectionItemIds.remove(last.collectionItemId)
        }
        updateSessionControls()
        sessionAdapter?.notifyDataSetChanged()
        captureGate.set(false)
        stability.reset()
        Toast.makeText(
            this,
            getString(R.string.rapid_scan_undo_success, last.option.displayName),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showUndoFailure() {
        if (isFinishing || isDestroyed) return
        undoLastButton.isEnabled = addedCopies.isNotEmpty()
        captureGate.set(false)
        Toast.makeText(this, R.string.rapid_scan_undo_error, Toast.LENGTH_LONG).show()
    }

    private fun showCardAddedNotification(option: CardEditionOption, card: CardInfo) {
        val price = PriceCurrency.format(this, card).ifBlank { getString(R.string.no_price) }
        val message = getString(
            R.string.rapid_scan_card_added_notice,
            option.displayName,
            option.setCode.uppercase(Locale.US),
            price
        )
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = Snackbar.make(root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.rapid_scan_view_session) { showRapidSession() }
            .also { it.show() }
    }

    private fun showRapidSession() {
        if (sessionEntries.isEmpty()) return
        val list = ListView(this).apply {
            dividerHeight = 1
            adapter = RapidSessionAdapter().also { sessionAdapter = it }
        }
        val count = sessionEntries.sumOf(RapidSessionEntry::scannedCopies)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rapid_scan_session_title, count))
            .setView(list)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private inner class RapidSessionAdapter : BaseAdapter() {
        override fun getCount(): Int = sessionEntries.size

        override fun getItem(position: Int): RapidSessionEntry = sessionEntries[position]

        override fun getItemId(position: Int): Long =
            getItem(position).card.collectionItemId.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.rapid_scan_session_item, parent, false)
            val entry = getItem(position)
            val card = entry.card
            view.findViewById<TextView>(R.id.rapidSessionName).text = card.name
            view.findViewById<TextView>(R.id.rapidSessionEdition).text = buildString {
                append(card.setName.orEmpty().ifBlank { card.setCode.orEmpty().uppercase(Locale.US) })
                card.collectorNumber.orEmpty().takeIf(String::isNotBlank)?.let {
                    append(" · #").append(it)
                }
                card.languageCode.orEmpty().takeIf(String::isNotBlank)?.let {
                    append(" · ").append(it.uppercase(Locale.US))
                }
            }
            view.findViewById<TextView>(R.id.rapidSessionCopies).text = getString(
                R.string.rapid_scan_session_copies,
                entry.scannedCopies,
                card.quantityCount
            )
            view.findViewById<TextView>(R.id.rapidSessionPrice).text =
                PriceCurrency.format(view.context, card).ifBlank { getString(R.string.no_price) }
            CardImageCache.displayKeepingCurrent(
                view.context,
                card.imgPath.orEmpty(),
                view.findViewById(R.id.rapidSessionImage)
            )
            return view
        }
    }

    private fun showEditionDetail(
        displayName: String,
        candidate: RapidRankedEdition,
        candidates: List<RapidRankedEdition>,
        printing: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        language: CardTextLanguageResult?,
        effectiveLanguage: String,
        visualResult: CardIdentificationResult?,
        fallbackFrame: CardFrameAnalysis?,
        guess: PrintingMetadataGuess
    ) {
        val option = candidate.option
        val message = getString(
            R.string.experimental_scan_edition_detail,
            option.setName,
            option.setCode.uppercase(Locale.US),
            option.collectorNumber,
            option.releaseDate.take(4).ifBlank { "—" },
            option.rarity.ifBlank { "—" },
            candidate.evidence.joinToString(" · ")
        )
        status.text = "$displayName\n$message"
        AlertDialog.Builder(this)
            .setTitle(displayName)
            .setMessage(message)
            .setPositiveButton(R.string.experimental_scan_back_to_editions) { _, _ ->
                showEditionCandidates(
                    displayName, candidates, printing, title,
                    language, effectiveLanguage, visualResult, fallbackFrame, guess
                )
            }
            .setNegativeButton(R.string.edition_scan_retake_photo) { _, _ -> returnToCamera() }
            .show()
    }

    private fun showOcrDetails(
        displayName: String,
        candidates: List<RapidRankedEdition>,
        printing: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        language: CardTextLanguageResult?,
        effectiveLanguage: String,
        visualResult: CardIdentificationResult?,
        fallbackFrame: CardFrameAnalysis?,
        guess: PrintingMetadataGuess
    ) {
        AlertDialog.Builder(this)
            .setTitle(displayName)
            .setMessage(resultSummary(
                displayName, printing, title, language, effectiveLanguage,
                visualResult, fallbackFrame, guess, includeRaw = true
            ))
            .setPositiveButton(R.string.experimental_scan_back_to_editions) { _, _ ->
                showEditionCandidates(
                    displayName, candidates, printing, title,
                    language, effectiveLanguage, visualResult, fallbackFrame, guess
                )
            }
            .setNegativeButton(R.string.edition_scan_retake_photo) { _, _ -> returnToCamera() }
            .show()
    }

    private fun showUnresolvedResult(
        ocr: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        language: CardTextLanguageResult?,
        visual: RapidVisualEvidence?,
        guess: PrintingMetadataGuess,
        matches: List<SetCardOption>,
        loadError: Throwable?,
        detectedName: String? = null
    ) {
        val resolvedName = detectedName
            ?: matches.map(SetCardOption::cardName).distinct().singleOrNull()
        val effectiveLanguage = effectiveLanguage(
            guess,
            language,
            titleText = title?.lines.orEmpty().joinToString(" ")
        )
        val parsed = resultSummary(
            resolvedName, ocr, title, language, effectiveLanguage,
            null, visual?.frame, guess, includeRaw = true
        )
        val resolved = when {
            resolvedName != null && matches.isNotEmpty() -> getString(
                R.string.experimental_scan_match,
                resolvedName,
                matches[0].setName,
                matches[0].setCode.uppercase(Locale.US),
                matches[0].collectorNumber
            )
            matches.size > 1 -> getString(R.string.experimental_scan_multiple_matches, matches.size)
            loadError != null -> getString(
                R.string.experimental_scan_lookup_error,
                loadError.message ?: loadError.javaClass.simpleName
            )
            resolvedName != null -> getString(R.string.experimental_scan_name_without_editions)
            else -> getString(R.string.experimental_scan_no_local_match)
        }
        val technicalMessage = "$parsed\n\n$resolved"
        status.text = technicalMessage
        instruction.setText(
            if (resolvedName != null) R.string.experimental_scan_identified
            else R.string.experimental_scan_adjust_and_retry
        )
        AlertDialog.Builder(this)
            .setTitle(
                if (matches.size == 1) R.string.experimental_scan_result_identified
                else R.string.rapid_scan_no_editions_title
            )
            .setMessage(resolved)
            .setPositiveButton(R.string.experimental_scan_read_again, null)
            .setNeutralButton(R.string.experimental_scan_show_ocr) { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.experimental_scan_result_title)
                    .setMessage(technicalMessage)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            .setNegativeButton(R.string.edition_scan_retake_photo) { _, _ -> returnToCamera() }
            .show()
    }

    private fun resultSummary(
        displayName: String?,
        ocr: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        language: CardTextLanguageResult?,
        effectiveLanguage: String,
        visualResult: CardIdentificationResult?,
        fallbackFrame: CardFrameAnalysis?,
        guess: PrintingMetadataGuess,
        includeRaw: Boolean = false
    ): String = buildString {
        val usedBorderZones = visualResult?.borderZones
            ?.takeIf { it.isNotEmpty() }
            ?: fallbackFrame?.borderZones.orEmpty()
        append(getString(R.string.experimental_scan_name_result, displayName ?: "—"))
        append("\n")
        append(getString(
            R.string.experimental_scan_parsed,
            guess.setCode ?: "—",
            guess.collectorNumber ?: "—",
            guess.languageCode ?: "—",
            guess.printingYear?.toString() ?: "—"
        ))
        if (effectiveLanguage.isNotBlank()) {
            append("\n")
            val confidence = language?.confidence ?: 0f
            if (confidence > 0f && language?.languageCode == effectiveLanguage) {
                append(getString(
                    R.string.experimental_scan_language_result,
                    effectiveLanguage.uppercase(Locale.US),
                    (confidence * 100).toInt()
                ))
            } else {
                append(getString(
                    R.string.experimental_scan_language_result_inferred,
                    effectiveLanguage.uppercase(Locale.US)
                ))
            }
        }
        val border = visualResult?.detectedBorder ?: fallbackFrame?.borderColor ?: CardBorderColor.UNKNOWN
        val borderConfidence = visualResult?.detectedBorderConfidence ?: fallbackFrame?.borderConfidence ?: 0.0
        if (border != CardBorderColor.UNKNOWN || usedBorderZones.isNotEmpty()) {
            append("\n")
            append(getString(
                R.string.experimental_scan_border_result,
                borderLabel(border),
                (borderConfidence * 100).toInt()
            ))
        }
        if (usedBorderZones.isNotEmpty()) {
            append("\n")
            append(borderRgbCompact(usedBorderZones))
        }
        if (ocr != null) {
            append("\n")
            append(getString(R.string.experimental_scan_variants, ocr.successfulVariants, ocr.attemptedVariants))
        }
        if (title != null) {
            append("\n")
            append(getString(
                R.string.experimental_scan_title_variants,
                title.successfulVariants,
                title.attemptedVariants
            ))
        }
        if (includeRaw) {
            append("\n\n")
            append(borderRgbDebug(usedBorderZones))
            append("\n\n")
            append(getString(R.string.experimental_scan_title_raw))
            append("\n")
            append(title?.lines?.joinToString("\n").orEmpty()
                .ifBlank { getString(R.string.experimental_scan_no_text) })
            append("\n\n")
            append(getString(R.string.experimental_scan_raw_text))
            append("\n")
            append(ocr?.rawText.orEmpty().ifBlank { getString(R.string.experimental_scan_no_text) })
            if (!language?.recognizedText.isNullOrBlank()) {
                append("\n\n")
                append(getString(R.string.experimental_scan_language_raw))
                append("\n")
                append(language?.recognizedText)
            }
        }
    }

    private fun borderRgbCompact(zones: List<CardBorderZone>): String =
        CardBorderSide.entries.joinToString(" · ", prefix = "RGB ") { side ->
            val sideZones = zones.filter { it.side == side }
            val brightest = sideZones.maxByOrNull {
                it.red * 299 + it.green * 587 + it.blue * 114
            }
            val whiteCount = sideZones.count { it.color == CardBorderColor.WHITE }
            val rgb = brightest?.let { "${it.red}/${it.green}/${it.blue}" } ?: "—"
            "${borderSideShortLabel(side)} $rgb [$whiteCount/${sideZones.size}]"
        }

    private fun borderRgbDebug(zones: List<CardBorderZone>): String = buildString {
        append("Lectura RGB del borde (blanco si R, G y B ≥ ")
        append(CardFrameAnalyzer.WHITE_CHANNEL_MIN)
        append("; también crema clara con luminosidad ≥ ")
        append(CardFrameAnalyzer.WHITE_CHANNEL_MIN)
        append(")")
        if (zones.isEmpty()) {
            append("\n—")
            return@buildString
        }
        CardBorderSide.entries.forEach { side ->
            val sideZones = zones.filter { it.side == side }.sortedBy { it.position }
            val whiteCount = sideZones.count { it.color == CardBorderColor.WHITE }
            append("\n")
            append(borderSideLabel(side))
            append(" [").append(whiteCount).append("/").append(sideZones.size).append("]: ")
            append(sideZones.joinToString(" · ") { zone ->
                "${zone.red}/${zone.green}/${zone.blue}" +
                    if (zone.color == CardBorderColor.WHITE) " ✓" else ""
            })
        }
        val totalWhite = zones.count { it.color == CardBorderColor.WHITE }
        val whiteSides = CardBorderSide.entries.count { side ->
            zones.count { it.side == side && it.color == CardBorderColor.WHITE } >= 2
        }
        append("\nTotal: ").append(totalWhite).append("/").append(zones.size)
            .append(" muestras blancas · ").append(whiteSides).append("/4 lados")
    }

    private fun borderSideLabel(side: CardBorderSide): String = when (side) {
        CardBorderSide.TOP -> getString(R.string.edition_scan_side_top)
        CardBorderSide.RIGHT -> getString(R.string.edition_scan_side_right)
        CardBorderSide.BOTTOM -> getString(R.string.edition_scan_side_bottom)
        CardBorderSide.LEFT -> getString(R.string.edition_scan_side_left)
    }

    private fun borderSideShortLabel(side: CardBorderSide): String = when (side) {
        CardBorderSide.TOP -> "A"
        CardBorderSide.RIGHT -> "D"
        CardBorderSide.BOTTOM -> "Ab"
        CardBorderSide.LEFT -> "I"
    }

    private fun borderLabel(border: CardBorderColor): String = when (border) {
        CardBorderColor.BLACK -> getString(R.string.edition_scan_border_black)
        CardBorderColor.WHITE -> getString(R.string.edition_scan_border_white)
        CardBorderColor.GOLD -> getString(R.string.edition_scan_border_gold)
        CardBorderColor.SILVER -> getString(R.string.edition_scan_border_silver)
        CardBorderColor.FULL_ART -> getString(R.string.edition_scan_border_full_art)
        CardBorderColor.MIXED -> getString(R.string.edition_scan_border_mixed)
        CardBorderColor.UNKNOWN -> getString(R.string.edition_scan_border_unknown)
    }

    private fun finishAnalysis() {
        analysisInFlight = false
        capture.isEnabled = true
        hideLoading()
    }

    private inner class ExperimentalEditionAdapter(
        private val candidates: List<RapidRankedEdition>
    ) : BaseAdapter() {
        override fun getCount(): Int = candidates.size

        override fun getItem(position: Int): RapidRankedEdition = candidates[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.edition_scan_candidate_item, parent, false)
            val candidate = getItem(position)
            val option = candidate.option
            val card = view.findViewById<MaterialCardView>(R.id.editionCandidateCard)
            val bestBadge = view.findViewById<TextView>(R.id.editionCandidateBestBadge)
            val accent = MagicPalette.secondaryColor(view.context)
            if (position == 0 && candidate.score > 0) {
                card.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 42))
                card.strokeColor = accent
                card.strokeWidth = (2f * resources.displayMetrics.density).toInt()
                bestBadge.setTextColor(accent)
                bestBadge.visibility = View.VISIBLE
            } else {
                card.setCardBackgroundColor(Color.TRANSPARENT)
                card.strokeWidth = 0
                bestBadge.visibility = View.GONE
            }
            view.findViewById<TextView>(R.id.editionCandidateTitle).text = getString(
                R.string.experimental_scan_candidate_set,
                option.setName,
                option.setCode.uppercase(Locale.US)
            )
            view.findViewById<TextView>(R.id.editionCandidateScores).text = getString(
                R.string.experimental_scan_candidate_metadata,
                option.collectorNumber,
                option.releaseDate.take(4).ifBlank { "—" },
                option.rarity.ifBlank { "—" }
            )
            val price = option.price?.let { amount ->
                PriceCurrency.format(view.context, amount, option.currency ?: PriceCurrency.EUR)
            } ?: getString(R.string.no_price)
            view.findViewById<TextView>(R.id.editionCandidateLanguage).text = getString(
                R.string.experimental_scan_candidate_price,
                price
            )
            view.findViewById<TextView>(R.id.editionCandidateBorder).text =
                candidate.evidence.joinToString(" · ")
            val cardImage = view.findViewById<ImageView>(R.id.editionCandidateCardImage)
            if (option.imageUrl.isNullOrBlank()) {
                cardImage.visibility = View.GONE
                cardImage.setOnClickListener(null)
            } else {
                cardImage.visibility = View.VISIBLE
                CardImageCache.display(view.context, option.imageUrl, cardImage)
                cardImage.setOnClickListener {
                    startActivity(Intent(this@RapidEditionScanActivity, CardImageActivity::class.java).apply {
                        putExtra(CardImageActivity.EXTRA_IMAGE_URL, option.imageUrl)
                        putExtra(CardImageActivity.EXTRA_SET_CODE, option.setCode)
                        putExtra(CardImageActivity.EXTRA_COLLECTOR_NUMBER, option.collectorNumber)
                        putExtra(CardImageActivity.EXTRA_FINISH, option.finish)
                    })
                }
            }
            SetSymbolLoader.display(
                view.context,
                option.setCode,
                view.findViewById(R.id.editionCandidateSetSymbol)
            )
            return view
        }
    }

    private fun showError(message: String) {
        hideLoading()
        debug.visibility = View.VISIBLE
        status.text = message
        if (!isFinishing) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun returnToCamera() {
        if (analysisInFlight) return
        correction.removeCallbacks(autoAnalyzeRunnable)
        autoAnalysisScheduled = false
        hideLoading()
        earlyLookupGeneration++
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = null
        earlyResult.visibility = View.GONE
        correction.clearPhoto()
        correction.visibility = View.GONE
        liveGuide.visibility = View.VISIBLE
        liveGuide.showDetection(null, 0f)
        correctionMode = false
        debug.visibility = View.GONE
        replaceDebugBitmap(null)
        stability.reset()
        quadHistory.clear()
        liveScanPhase = RapidLiveScanPhase.NAME
        recognizedNameMatch = null
        missedFrames = 0
        lastDetected = null
        captureGate.set(false)
        cancel.setText(android.R.string.cancel)
        capture.setText(R.string.experimental_scan_manual_capture)
        capture.isEnabled = false
        instruction.setText(R.string.experimental_scan_live_reading_name)
        ensureCamera()
    }

    private fun replaceDebugBitmap(bitmap: Bitmap?) {
        crop.setImageBitmap(bitmap)
        debugBitmap?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
        debugBitmap = bitmap
    }

    override fun onResume() {
        super.onResume()
        if (!correctionMode) ensureCamera()
    }

    override fun onPause() {
        cameraProvider?.unbindAll()
        imageAnalysis = null
        imageCapture = null
        super.onPause()
    }

    override fun onDestroy() {
        correction.removeCallbacks(autoAnalyzeRunnable)
        feedbackSnackbar?.dismiss()
        feedbackSnackbar = null
        toneGenerator?.release()
        toneGenerator = null
        replaceDebugBitmap(null)
        correction.clearPhoto()
        printingLineOcr.close()
        cardTitleOcr.close()
        liveCardNameOcr.close()
        cardLanguageDetector.close()
        analysisExecutor.shutdownNow()
        photoExecutor.shutdownNow()
        metadataExecutor.shutdownNow()
        cameraProvider?.unbindAll()
        cameraProvider = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            ensureCamera()
        } else if (requestCode == RC_CAMERA) {
            finish()
        }
    }

    private fun decodePhoto(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_PHOTO_SIDE || bounds.outHeight / sample > MAX_PHOTO_SIDE) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    companion object {
        private const val PERF_TAG = "RapidScanPerf"
        private const val LIVE_TIMING_SAMPLE_SIZE = 20
        const val EXTRA_SESSION_CARD_IDS = "rapid_session_card_ids"
        const val RC_CAMERA = 902
        const val ANALYSIS_INTERVAL_MS = 70L
        const val NAME_ANALYSIS_INTERVAL_MS = 180L
        const val AUTO_ANALYZE_DELAY_MS = 1_150L
        const val MISSES_BEFORE_RESET = 3
        const val MAX_PHOTO_SIDE = 1_800
        const val MAX_SET_CANDIDATES = 4
        const val MAX_EDITION_RESULTS = 15
        const val CONSENSUS_FRAMES = 7
        const val MAX_CONSENSUS_JUMP = .065f
        const val MAX_STILL_LIVE_DISAGREEMENT = .045f
        const val STILL_WEIGHT = .86f
    }
}
