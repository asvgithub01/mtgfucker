package io.asv.mtgocr.ocrreader

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.View
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
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.SetCardOption
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    private val openCvDetector = OpenCvCardDetector()
    private val stability = AutoCaptureStability()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val photoExecutor = Executors.newSingleThreadExecutor()
    private val metadataExecutor = Executors.newSingleThreadExecutor()
    private val captureGate = AtomicBoolean(false)
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
            } else if (captureGate.compareAndSet(false, true)) {
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
        instruction.setText(R.string.experimental_scan_finding_edges)
    }

    private fun analyzeLiveFrame(image: ImageProxy) {
        try {
            if (captureGate.get() || correctionMode) return
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
            capture.isEnabled = true
            instruction.setText(R.string.experimental_scan_finding_edges)
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
        status.setText(R.string.experimental_scan_reading_bottom)
        instruction.setText(R.string.experimental_scan_reading_bottom)
        replaceDebugBitmap(null)
        printingLineOcr.recognize(corrected) { result, error ->
            corrected.recycle()
            if (isFinishing || isDestroyed) {
                result?.preview?.recycle()
                return@recognize
            }
            if (result == null || error != null) {
                analysisInFlight = false
                capture.isEnabled = true
                showError(error?.message ?: getString(R.string.experimental_scan_ocr_error))
                return@recognize
            }
            replaceDebugBitmap(result.preview)
            val guess = PrintingMetadataParser.parse(result.rawText, knownSetCodes)
            val collector = guess.collectorNumber
            val setCodes = guess.setCodeCandidates.asSequence()
                .filter { knownSetCodes.isEmpty() || it in knownSetCodes }
                .take(MAX_SET_CANDIDATES)
                .toList()
            if (setCodes.isEmpty() || collector == null) {
                analysisInFlight = false
                capture.isEnabled = true
                showResult(result, guess, emptyList(), null)
                return@recognize
            }
            status.setText(R.string.experimental_scan_resolving_printing)
            repository.resolvePrintingMetadata(setCodes, collector) { cards, loadError ->
                if (isFinishing || isDestroyed) return@resolvePrintingMetadata
                analysisInFlight = false
                capture.isEnabled = true
                val matches = cards.distinctBy { Triple(it.cardName, it.setCode, it.collectorNumber) }
                showResult(result, guess, matches, loadError)
            }
        }
    }

    private fun showResult(
        ocr: PrintingLineOcrResult,
        guess: PrintingMetadataGuess,
        matches: List<SetCardOption>,
        loadError: Throwable?
    ) {
        val parsed = buildString {
            append(getString(
                R.string.experimental_scan_parsed,
                guess.setCode ?: "—",
                guess.collectorNumber ?: "—",
                guess.languageCode ?: "—"
            ))
            append("\n")
            append(getString(
                R.string.experimental_scan_variants,
                ocr.successfulVariants,
                ocr.attemptedVariants
            ))
        }
        val resolved = when {
            matches.size == 1 -> getString(
                R.string.experimental_scan_match,
                matches[0].cardName,
                matches[0].setName,
                matches[0].setCode.uppercase(Locale.US),
                matches[0].collectorNumber
            )
            matches.size > 1 -> getString(R.string.experimental_scan_multiple_matches, matches.size)
            loadError != null -> getString(
                R.string.experimental_scan_lookup_error,
                loadError.message ?: loadError.javaClass.simpleName
            )
            else -> getString(R.string.experimental_scan_no_local_match)
        }
        val raw = ocr.rawText.ifBlank { getString(R.string.experimental_scan_no_text) }
        val message = "$parsed\n\n$resolved\n\n${getString(R.string.experimental_scan_raw_text)}\n$raw"
        status.text = message
        instruction.setText(
            if (matches.size == 1) R.string.experimental_scan_identified
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
        missedFrames = 0
        lastDetected = null
        captureGate.set(false)
        cancel.setText(android.R.string.cancel)
        capture.setText(R.string.experimental_scan_manual_capture)
        capture.isEnabled = true
        instruction.setText(R.string.experimental_scan_finding_edges)
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
        const val MISSES_BEFORE_RESET = 3
        const val MAX_PHOTO_SIDE = 2_400
        const val MAX_SET_CANDIDATES = 4
    }
}
