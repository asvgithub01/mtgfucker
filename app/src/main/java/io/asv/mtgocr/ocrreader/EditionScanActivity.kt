package io.asv.mtgocr.ocrreader

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Camera
import android.os.Bundle
import android.os.SystemClock
import android.util.SparseArray
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.SetSymbolIdentificationResult
import io.asv.mtgocr.ocrreader.ui.camera.CameraSource
import io.asv.mtgocr.ocrreader.ui.camera.CameraSourcePreview
import java.io.IOException
import java.util.Locale

/** Focused second step: OCR supplies the name; this screen only reads the edition symbol. */
class EditionScanActivity : AppCompatActivity() {
    private lateinit var preview: CameraSourcePreview
    private lateinit var capture: Button
    private lateinit var debug: View
    private lateinit var crop: ImageView
    private lateinit var status: TextView
    private val repository by lazy { CardRepository.get(this) }
    private val detector = object : Detector<Int>() {
        override fun detect(frame: Frame): SparseArray<Int> = SparseArray()
    }
    private var cameraSource: CameraSource? = null
    private var debugBitmap: Bitmap? = null
    private var comparisonStartedAt = 0L

    private val cardName by lazy { intent.getStringExtra(EXTRA_CARD_NAME).orEmpty() }
    private val displayName by lazy {
        intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty().ifBlank { cardName }
    }
    private val language by lazy { intent.getStringExtra(EXTRA_LANGUAGE).orEmpty() }
    private val preferFoil by lazy { intent.getBooleanExtra(EXTRA_PREFER_FOIL, false) }
    private val lockedSets by lazy {
        intent.getStringArrayListExtra(EXTRA_LOCKED_SETS).orEmpty().toSet()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edition_scan)
        preview = findViewById(R.id.editionCameraPreview)
        capture = findViewById(R.id.editionScanCapture)
        debug = findViewById(R.id.editionScanDebug)
        crop = findViewById(R.id.editionScanCrop)
        status = findViewById(R.id.editionScanStatus)
        findViewById<TextView>(R.id.editionScanCardName).text =
            getString(R.string.edition_scan_title) + " · " + displayName
        findViewById<Button>(R.id.editionScanCancel).setOnClickListener { finish() }
        capture.setOnClickListener { captureSymbol() }
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
        startCamera()
    }

    private fun startCamera() {
        val source = cameraSource ?: return
        try {
            preview.start(source)
        } catch (_: IOException) {
            source.release()
            cameraSource = null
            showFailure()
        } catch (_: SecurityException) {
            showFailure()
        }
    }

    private fun captureSymbol() {
        val source = cameraSource ?: return
        capture.isEnabled = false
        debug.visibility = View.VISIBLE
        status.setText(R.string.edition_scan_comparing)
        replaceDebugBitmap(null)
        comparisonStartedAt = SystemClock.elapsedRealtime()
        try {
            source.takePicture(null) { jpeg ->
                repository.identifyCardSetSymbol(
                    cardName,
                    language,
                    jpeg,
                    lockedSets,
                    preferFoil
                ) { result, error ->
                    capture.isEnabled = true
                    if (error != null || result.candidates.isEmpty()) {
                        result.setSymbolCrop?.let(::replaceDebugBitmap)
                        showFailure()
                    } else {
                        showResult(result)
                    }
                }
            }
        } catch (_: RuntimeException) {
            capture.isEnabled = true
            showFailure()
        }
    }

    private fun showResult(result: SetSymbolIdentificationResult) {
        replaceDebugBitmap(result.setSymbolCrop)
        val elapsed = (SystemClock.elapsedRealtime() - comparisonStartedAt).coerceAtLeast(0L)
        status.text = getString(
            R.string.edition_scan_result,
            result.detectedLanguage.ifBlank { "—" },
            result.languageFilteredOut,
            result.comparedSymbols,
            elapsed
        )
        val labels = result.candidates.map { candidate ->
            getString(
                R.string.edition_scan_candidate,
                candidate.option.setName,
                candidate.option.setCode.uppercase(Locale.US),
                ((1.0 - candidate.symbolDistance) * 100.0).toInt().coerceIn(0, 100)
            )
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.edition_scan_choose)
            .setItems(labels) { _, which ->
                val selected = result.candidates[which].option
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(EXTRA_SET_CODE, selected.setCode)
                )
                finish()
            }
            .setNegativeButton(R.string.edition_scan_read_symbol, null)
            .show()
    }

    private fun showFailure() {
        debug.visibility = View.VISIBLE
        status.setText(R.string.edition_scan_no_match)
    }

    private fun replaceDebugBitmap(bitmap: Bitmap?) {
        crop.setImageBitmap(bitmap)
        debugBitmap?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
        debugBitmap = bitmap
    }

    override fun onResume() {
        super.onResume()
        ensureCamera()
    }

    override fun onPause() {
        preview.stop()
        super.onPause()
    }

    override fun onDestroy() {
        replaceDebugBitmap(null)
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

    companion object {
        const val EXTRA_CARD_NAME = "edition.card_name"
        const val EXTRA_DISPLAY_NAME = "edition.display_name"
        const val EXTRA_LANGUAGE = "edition.language"
        const val EXTRA_PREFER_FOIL = "edition.prefer_foil"
        const val EXTRA_LOCKED_SETS = "edition.locked_sets"
        const val EXTRA_SET_CODE = "edition.set_code"
        private const val RC_CAMERA = 901
    }
}
