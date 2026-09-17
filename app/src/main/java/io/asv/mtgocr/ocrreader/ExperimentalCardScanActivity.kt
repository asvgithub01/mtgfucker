package io.asv.mtgocr.ocrreader

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
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
import com.google.android.material.card.MaterialCardView
import org.opencv.android.OpenCVLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private class PendingCardOcr {
    var remaining = 4
    var printing: PrintingLineOcrResult? = null
    var title: CardTitleOcrResult? = null
    var nameMatch: LocalCardNameMatch? = null
    var nameCandidates: List<LocalCardNameMatch> = emptyList()
    var language: CardTextLanguageResult? = null
    var visual: ExperimentalVisualEvidence? = null
    var error: Throwable? = null
}

private data class ExperimentalVisualEvidence(
    val jpeg: ByteArray,
    val frame: CardFrameAnalysis
)

private data class RankedEdition(
    val option: CardEditionOption,
    val score: Int,
    val evidence: List<String>
)

private enum class LiveScanPhase {
    NAME,
    EDGES
}

/** CameraX/OpenCV laboratory kept separate from the maintained scanner. */
class ExperimentalCardScanActivity : AppCompatActivity() {
    private lateinit var preview: PreviewView
    private lateinit var liveGuide: ExperimentalCardGuideView
    private lateinit var correction: CardCropAdjustView
    private lateinit var capture: Button
    private lateinit var cancel: Button
    private lateinit var instruction: TextView
    private lateinit var debug: View
    private lateinit var crop: ImageView
    private lateinit var status: TextView

    private val repository by lazy { CardRepository.get(this) }
    private val printingLineOcr = PrintingLineOcr()
    private val cardTitleOcr = CardTitleOcr()
    private val liveCardNameOcr = LiveCardNameOcr()
    private val cardLanguageDetector = CardTextLanguageDetector()
    private val openCvDetector = OpenCvCardDetector()
    private val stability = AutoCaptureStability()
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
    private var cameraStarting = false
    private var openCvReady = false
    private var missedFrames = 0
    private var lastAnalyzedAt = 0L
    @Volatile private var liveScanPhase = LiveScanPhase.NAME
    private var recognizedNameMatch: LocalCardNameMatch? = null
    @Volatile private var lastDetected: OpenCvDetectedQuad? = null
    @Volatile private var knownSetCodes: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_experimental_card_scan)
        preview = findViewById(R.id.experimentalCameraPreview)
        liveGuide = findViewById(R.id.experimentalScanLiveGuide)
        correction = findViewById(R.id.experimentalCropCorrection)
        capture = findViewById(R.id.experimentalScanCapture)
        cancel = findViewById(R.id.experimentalScanCancel)
        instruction = findViewById(R.id.experimentalScanInstruction)
        debug = findViewById(R.id.experimentalScanDebug)
        crop = findViewById(R.id.experimentalScanCrop)
        status = findViewById(R.id.experimentalScanStatus)
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
            } else if (liveScanPhase == LiveScanPhase.EDGES &&
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
        val camera = provider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            previewUseCase,
            analysis,
            stillCapture
        )
        imageAnalysis = analysis
        imageCapture = stillCapture
        val center = preview.meteringPointFactory.createPoint(.5f, .5f)
        camera.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(
                center,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).setAutoCancelDuration(2, TimeUnit.SECONDS).build()
        )
        capture.isEnabled = liveScanPhase == LiveScanPhase.EDGES
        instruction.setText(
            if (liveScanPhase == LiveScanPhase.NAME) R.string.experimental_scan_live_reading_name
            else R.string.experimental_scan_finding_edges
        )
    }

    private fun analyzeLiveFrame(image: ImageProxy) {
        if (captureGate.get() || correctionMode) {
            image.close()
            return
        }
        if (liveScanPhase == LiveScanPhase.NAME) {
            analyzeLiveName(image)
            return
        }
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzedAt < ANALYSIS_INTERVAL_MS) return
            lastAnalyzedAt = now
            val detected = openCvDetector.detect(image)
            if (detected == null) {
                missedFrames++
                if (missedFrames >= MISSES_BEFORE_RESET) {
                    stability.reset()
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
            lastDetected = detected
            val decision = stability.observe(detected.normalizedCorners, detected.confidence, now)
            runOnUiThread {
                if (!correctionMode) {
                    liveGuide.showDetection(detected, decision.progress)
                    instruction.setText(
                        if (decision.progress >= .72f) R.string.experimental_scan_hold_steady
                        else R.string.experimental_scan_card_detected
                    )
                }
            }
            if (decision.shouldCapture && captureGate.compareAndSet(false, true)) {
                runOnUiThread {
                    instruction.setText(R.string.experimental_scan_auto_capture)
                    capturePhoto(detected, automatic = true)
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
                    liveScanPhase != LiveScanPhase.NAME || isFinishing || isDestroyed ||
                    !liveNameLookupGate.compareAndSet(false, true)
                ) {
                    return@recognize
                }
                repository.matchLocalOcrText(candidates) { match ->
                    liveNameLookupGate.set(false)
                    if (match == null || correctionMode || liveScanPhase != LiveScanPhase.NAME ||
                        isFinishing || isDestroyed
                    ) {
                        return@matchLocalOcrText
                    }
                    recognizedNameMatch = match
                    liveScanPhase = LiveScanPhase.EDGES
                    stability.reset()
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

    private fun capturePhoto(detected: OpenCvDetectedQuad?, automatic: Boolean) {
        val stillCapture = imageCapture
        if (stillCapture == null) {
            captureGate.set(false)
            return
        }
        capture.isEnabled = false
        instruction.setText(
            if (automatic) R.string.experimental_scan_auto_capture
            else R.string.edition_scan_freezing_photo
        )
        debug.visibility = View.GONE
        replaceDebugBitmap(null)
        val output = File.createTempFile("experimental-card-", ".jpg", cacheDir)
        stillCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(output).build(),
            photoExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val bitmap = decodePhoto(output)
                    output.delete()
                    if (bitmap == null) {
                        cameraFailure()
                        return
                    }
                    val corners = detected?.cornersFor(bitmap.width, bitmap.height)
                        ?: openCvDetector.detect(bitmap)?.cornersFor(bitmap.width, bitmap.height)
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

    private fun showCapturedPhoto(bitmap: Bitmap, corners: Array<PointF>, automatic: Boolean) {
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
        if (automatic) correction.post { analyzeCorrectedPhoto() }
    }

    private fun cameraFailure(message: String = getString(R.string.experimental_scan_camera_error)) {
        runOnUiThread {
            captureGate.set(false)
            stability.reset()
            capture.isEnabled = liveScanPhase == LiveScanPhase.EDGES
            instruction.setText(
                if (liveScanPhase == LiveScanPhase.NAME) R.string.experimental_scan_live_reading_name
                else R.string.experimental_scan_finding_edges
            )
            showError(message)
        }
    }

    private fun analyzeCorrectedPhoto() {
        if (analysisInFlight) return
        val corrected = correction.extractCardBitmap() ?: run {
            instruction.setText(R.string.edition_scan_invalid_crop)
            return
        }
        analysisInFlight = true
        capture.isEnabled = false
        debug.visibility = View.VISIBLE
        status.setText(R.string.experimental_scan_reading_name_first)
        instruction.setText(R.string.experimental_scan_reading_name_first)
        replaceDebugBitmap(null)
        val pending = PendingCardOcr().apply { nameMatch = recognizedNameMatch }
        fun completeIfReady(): Boolean = synchronized(pending) {
            pending.remaining--
            pending.remaining == 0
        }
        cardTitleOcr.recognize(corrected) { result, error ->
            synchronized(pending) {
                pending.title = result
                if (error != null && pending.error == null) pending.error = error
            }
            if (result == null || result.lines.isEmpty()) {
                if (completeIfReady()) finishCombinedOcr(corrected, pending)
            } else {
                repository.matchLocalPhotoText(result.lines) { matches ->
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
        printingLineOcr.recognize(corrected) { result, error ->
            synchronized(pending) {
                pending.printing = result
                if (error != null && pending.error == null) pending.error = error
            }
            if (completeIfReady()) finishCombinedOcr(corrected, pending)
        }
        cardLanguageDetector.detect(corrected, "") { result ->
            synchronized(pending) { pending.language = result }
            if (completeIfReady()) finishCombinedOcr(corrected, pending)
        }
        photoExecutor.execute {
            val evidence = runCatching {
                val frame = CardFrameAnalyzer.analyzeTightCard(corrected)
                val jpeg = ByteArrayOutputStream().use { output ->
                    corrected.compress(Bitmap.CompressFormat.JPEG, 94, output)
                    output.toByteArray()
                }
                ExperimentalVisualEvidence(jpeg, frame)
            }
            synchronized(pending) {
                pending.visual = evidence.getOrNull()
                evidence.exceptionOrNull()?.let { if (pending.error == null) pending.error = it }
            }
            if (completeIfReady()) finishCombinedOcr(corrected, pending)
        }
    }

    private fun finishCombinedOcr(card: Bitmap, pending: PendingCardOcr) {
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
        visual: ExperimentalVisualEvidence?,
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
                        effectiveLanguage(guess, language, matchingOcrEvidence?.language.orEmpty()),
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
                        effectiveLanguage(guess, language, preferredMatch.language),
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
                effectiveLanguage(guess, language, preferredMatch.language),
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
        visual: ExperimentalVisualEvidence?,
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
        repository.identifyCardArtwork(
            cardName = canonicalName,
            languageCode = languageCode,
            jpeg = visual.jpeg,
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
                    finishAnalysis()
                    showUnresolvedResult(
                        printing,
                        title,
                        languageEvidence,
                        visual,
                        guess,
                        exactMatches,
                        IllegalStateException(
                            getString(R.string.experimental_scan_no_language_printings, languageCode.uppercase(Locale.US))
                        ),
                        displayName
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
        titleLanguage: String = ""
    ): String = CardLanguageEvidenceResolver.resolve(
        footerLanguage = guess.languageCode,
        detectedRulesLanguage = detected?.languageCode,
        detectedRulesConfidence = detected?.confidence ?: 0f,
        matchedTitleLanguage = titleLanguage
    )

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
                    if (localized.isNotEmpty()) {
                        val ranked = rankEditions(localized, guess, languageCode)
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
        languageCode: String = ""
    ): List<RankedEdition> {
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
                RankedEdition(
                    option,
                    score,
                    evidence.ifEmpty { listOf(getString(R.string.experimental_scan_evidence_name_only)) }
                )
            }
            .sortedWith(compareByDescending<RankedEdition> { it.score }
                .thenByDescending { it.option.releaseDate })
            .take(MAX_EDITION_RESULTS)
    }

    private fun rankVisualEditions(
        result: CardIdentificationResult,
        guess: PrintingMetadataGuess,
        languageCode: String
    ): List<RankedEdition> {
        val setCandidates = guess.setCodeCandidates.map { it.uppercase(Locale.US) }
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
                RankedEdition(option, score, evidence)
            }
            .sortedWith(compareByDescending<RankedEdition> { it.score }
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
        candidates: List<RankedEdition>,
        printing: PrintingLineOcrResult?,
        title: CardTitleOcrResult?,
        language: CardTextLanguageResult?,
        effectiveLanguage: String,
        visualResult: CardIdentificationResult?,
        fallbackFrame: CardFrameAnalysis?,
        guess: PrintingMetadataGuess
    ) {
        val summary = resultSummary(
            displayName, printing, title, language, effectiveLanguage, visualResult, fallbackFrame, guess
        )
        status.text = summary
        instruction.setText(R.string.experimental_scan_identified)
        AlertDialog.Builder(this)
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
            .setNegativeButton(R.string.edition_scan_retake_photo) { _, _ -> returnToCamera() }
            .show()
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
                    setResult(RESULT_OK)
                    Toast.makeText(
                        this,
                        getString(
                            R.string.experimental_scan_card_added,
                            option.displayName,
                            option.setName,
                            card.quantityCount
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    returnToCamera()
                }
            }
        }
    }

    private fun showEditionDetail(
        displayName: String,
        candidate: RankedEdition,
        candidates: List<RankedEdition>,
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
        candidates: List<RankedEdition>,
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
        visual: ExperimentalVisualEvidence?,
        guess: PrintingMetadataGuess,
        matches: List<SetCardOption>,
        loadError: Throwable?,
        detectedName: String? = null
    ) {
        val resolvedName = detectedName
            ?: matches.map(SetCardOption::cardName).distinct().singleOrNull()
        val effectiveLanguage = effectiveLanguage(guess, language)
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
        val message = "$parsed\n\n$resolved"
        status.text = message
        instruction.setText(
            if (resolvedName != null) R.string.experimental_scan_identified
            else R.string.experimental_scan_adjust_and_retry
        )
        AlertDialog.Builder(this)
            .setTitle(
                if (matches.size == 1) R.string.experimental_scan_result_identified
                else R.string.experimental_scan_result_title
            )
            .setMessage(message)
            .setPositiveButton(R.string.experimental_scan_read_again, null)
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
        if (border != CardBorderColor.UNKNOWN) {
            append("\n")
            append(getString(
                R.string.experimental_scan_border_result,
                borderLabel(border),
                (borderConfidence * 100).toInt()
            ))
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
    }

    private inner class ExperimentalEditionAdapter(
        private val candidates: List<RankedEdition>
    ) : BaseAdapter() {
        override fun getCount(): Int = candidates.size

        override fun getItem(position: Int): RankedEdition = candidates[position]

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
            SetSymbolLoader.display(
                view.context,
                option.setCode,
                view.findViewById(R.id.editionCandidateSetSymbol)
            )
            return view
        }
    }

    private fun showError(message: String) {
        debug.visibility = View.VISIBLE
        status.text = message
        if (!isFinishing) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun returnToCamera() {
        if (analysisInFlight) return
        correction.clearPhoto()
        correction.visibility = View.GONE
        liveGuide.visibility = View.VISIBLE
        liveGuide.showDetection(null, 0f)
        correctionMode = false
        debug.visibility = View.GONE
        replaceDebugBitmap(null)
        stability.reset()
        liveScanPhase = LiveScanPhase.NAME
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

    private companion object {
        const val RC_CAMERA = 902
        const val ANALYSIS_INTERVAL_MS = 90L
        const val NAME_ANALYSIS_INTERVAL_MS = 240L
        const val MISSES_BEFORE_RESET = 3
        const val MAX_PHOTO_SIDE = 2_400
        const val MAX_SET_CANDIDATES = 4
        const val MAX_EDITION_RESULTS = 15
    }
}
