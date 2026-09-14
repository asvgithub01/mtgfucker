package io.asv.mtgocr.ocrreader

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Camera
import android.os.Bundle
import android.util.SparseArray
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.SetCardOption
import io.asv.mtgocr.ocrreader.ui.camera.CameraSource
import io.asv.mtgocr.ocrreader.ui.camera.CameraSourcePreview
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

/** Isolated copy of the current capture/corner-correction flow for camera recognition experiments. */
class ExperimentalCardScanActivity : AppCompatActivity() {
    private lateinit var preview: CameraSourcePreview
    private lateinit var liveGuide: View
    private lateinit var correction: CardCropAdjustView
    private lateinit var capture: Button
    private lateinit var cancel: Button
    private lateinit var instruction: TextView
    private lateinit var debug: View
    private lateinit var crop: ImageView
    private lateinit var status: TextView
    private val detector = object : Detector<Int>() {
        override fun detect(frame: Frame): SparseArray<Int> = SparseArray()
    }
    private val repository by lazy { CardRepository.get(this) }
    private val printingLineOcr = PrintingLineOcr()
    private val photoExecutor = Executors.newSingleThreadExecutor()
    private val metadataExecutor = Executors.newSingleThreadExecutor()
    private var cameraSource: CameraSource? = null
    private var debugBitmap: Bitmap? = null
    private var correctionMode = false
    private var analysisInFlight = false
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
        cancel.setOnClickListener {
            if (correctionMode) returnToCamera() else finish()
        }
        capture.setOnClickListener {
            if (correctionMode) analyzeCorrectedPhoto() else takePhotoForCorrection()
        }
        metadataExecutor.execute {
            knownSetCodes = CardDatabase.get(this).cardDao().magicSets()
                .mapTo(LinkedHashSet()) { it.code.uppercase(Locale.US) }
        }
    }

    private fun ensureCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), RC_CAMERA)
            return
        }
        if (cameraSource == null) {
            cameraSource = CameraSource.Builder(applicationContext, detector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1280, 1024)
                .setRequestedFps(2f)
                .setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
                .build()
        }
        try {
            preview.start(cameraSource!!)
        } catch (_: IOException) {
            cameraSource?.release()
            cameraSource = null
            showError(getString(R.string.experimental_scan_camera_error))
        } catch (_: SecurityException) {
            showError(getString(R.string.experimental_scan_camera_error))
        }
    }

    private fun takePhotoForCorrection() {
        val source = cameraSource ?: return
        capture.isEnabled = false
        instruction.setText(R.string.edition_scan_freezing_photo)
        debug.visibility = View.GONE
        replaceDebugBitmap(null)
        try {
            source.takePicture(null) { jpeg ->
                photoExecutor.execute {
                    val bitmap = decodePhoto(jpeg)
                    val detected = bitmap?.let(CardQuadrilateralDetector::detect)
                    val fallback = if (detected == null) bitmap?.let { CardFrameAnalyzer.analyze(it).bounds }
                        else null
                    runOnUiThread {
                        if (isFinishing || isDestroyed) {
                            bitmap?.recycle()
                            return@runOnUiThread
                        }
                        if (bitmap == null || (detected == null && fallback == null)) {
                            capture.isEnabled = true
                            instruction.setText(R.string.experimental_scan_align_card)
                            showError(getString(R.string.experimental_scan_card_not_found))
                        } else {
                            preview.stop()
                            if (detected != null) correction.setPhoto(bitmap, detected.corners)
                            else correction.setPhoto(bitmap, fallback!!)
                            correction.visibility = View.VISIBLE
                            liveGuide.visibility = View.GONE
                            correctionMode = true
                            cancel.setText(R.string.edition_scan_retake_photo)
                            capture.setText(R.string.experimental_scan_read_printing)
                            capture.isEnabled = true
                            instruction.setText(
                                if (detected != null) R.string.edition_scan_adjust_corners_auto
                                else R.string.edition_scan_adjust_corners
                            )
                        }
                    }
                }
            }
        } catch (_: RuntimeException) {
            capture.isEnabled = true
            showError(getString(R.string.experimental_scan_camera_error))
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
            val setCode = guess.setCode
            val collector = guess.collectorNumber
            if (setCode == null || collector == null) {
                analysisInFlight = false
                capture.isEnabled = true
                showResult(result, guess, emptyList(), null)
                return@recognize
            }
            status.setText(R.string.experimental_scan_resolving_printing)
            repository.resolvePrintingMetadata(setCode, collector) { cards, loadError ->
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
            append(getString(R.string.experimental_scan_variants, ocr.successfulVariants))
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
        correctionMode = false
        debug.visibility = View.GONE
        replaceDebugBitmap(null)
        cancel.setText(android.R.string.cancel)
        capture.setText(R.string.edition_scan_take_photo)
        instruction.setText(R.string.experimental_scan_align_card)
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
        preview.stop()
        super.onPause()
    }

    override fun onDestroy() {
        replaceDebugBitmap(null)
        correction.clearPhoto()
        printingLineOcr.close()
        photoExecutor.shutdownNow()
        metadataExecutor.shutdownNow()
        preview.release()
        cameraSource = null
        detector.release()
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

    private fun decodePhoto(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1_600 || bounds.outHeight / sample > 1_600) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null
        if (decoded.height >= decoded.width) return decoded
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private companion object {
        const val RC_CAMERA = 902
    }
}
