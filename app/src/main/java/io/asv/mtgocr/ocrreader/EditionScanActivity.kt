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
import io.asv.mtgocr.ocrreader.data.CardIdentificationCandidate
import io.asv.mtgocr.ocrreader.data.CardIdentificationResult
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.ui.camera.CameraSource
import io.asv.mtgocr.ocrreader.ui.camera.CameraSourcePreview
import java.io.IOException
import java.util.Locale

/** Focused second step: OCR supplies the name; this screen gathers visual edition evidence. */
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
        capture.setOnClickListener { captureCard() }
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

    private fun captureCard() {
        val source = cameraSource ?: return
        capture.isEnabled = false
        debug.visibility = View.VISIBLE
        status.setText(R.string.edition_scan_comparing)
        replaceDebugBitmap(null)
        comparisonStartedAt = SystemClock.elapsedRealtime()
        try {
            source.takePicture(null) { jpeg ->
                repository.identifyCardArtwork(
                    cardName,
                    language,
                    jpeg,
                    lockedSets,
                    preferFoil
                ) { result, error ->
                    capture.isEnabled = true
                    if (error != null || result.candidates.isEmpty()) {
                        replaceDebugBitmap(result.analysisPreview ?: result.setSymbolCrop)
                        recycleUnusedResultBitmaps(result)
                        showFailure(result)
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

    private fun showResult(result: CardIdentificationResult) {
        replaceDebugBitmap(result.analysisPreview ?: result.setSymbolCrop)
        recycleUnusedResultBitmaps(result)
        val elapsed = (SystemClock.elapsedRealtime() - comparisonStartedAt).coerceAtLeast(0L)
        status.text = getString(
            R.string.edition_scan_result,
            (result.boundaryConfidence * 100).toInt().coerceIn(0, 100),
            borderLabel(result.detectedBorder),
            (result.detectedBorderConfidence * 100).toInt().coerceIn(0, 100),
            result.borderSampleCount,
            result.detectedLanguage.ifBlank { "—" },
            result.languageFilteredOut,
            result.comparedImages,
            elapsed,
            (result.glareRatio * 100).toInt().coerceIn(0, 100)
        )
        val labels = result.candidates.map { candidate ->
            getString(
                R.string.edition_scan_candidate,
                candidate.option.setName,
                candidate.option.setCode.uppercase(Locale.US),
                similarity(candidate.distance),
                similarity(candidate.artworkDistance),
                similarity(candidate.setSymbolDistance),
                borderMatchLabel(candidate.borderMatches)
            )
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(
                if (result.confident) R.string.edition_scan_confirmed
                else R.string.edition_scan_probable
            )
            .setItems(labels) { _, which ->
                showCandidateExplanation(result.candidates[which], result)
            }
            .setNegativeButton(R.string.edition_scan_retry, null)
            .show()
    }

    private fun showCandidateExplanation(
        candidate: CardIdentificationCandidate,
        result: CardIdentificationResult
    ) {
        val borderEvidence = when (candidate.borderMatches) {
            true -> getString(
                R.string.edition_scan_evidence_border_match,
                borderLabel(result.detectedBorder)
            )
            false -> getString(
                R.string.edition_scan_evidence_border_mismatch,
                borderLabel(result.detectedBorder),
                borderLabel(candidate.referenceBorder)
            )
            null -> getString(R.string.edition_scan_evidence_border_unknown)
        }
        val message = getString(
            R.string.edition_scan_explanation,
            similarity(candidate.distance),
            similarity(candidate.artworkDistance),
            similarity(candidate.setSymbolDistance),
            borderEvidence,
            (result.boundaryConfidence * 100).toInt().coerceIn(0, 100),
            result.borderSampleCount,
            (result.glareRatio * 100).toInt().coerceIn(0, 100),
            (result.sharpness * 100).toInt().coerceIn(0, 100),
            borderRgbSummary(result)
        )
        AlertDialog.Builder(this)
            .setTitle("${candidate.option.setName} (${candidate.option.setCode.uppercase(Locale.US)})")
            .setMessage(message)
            .setPositiveButton(R.string.edition_scan_use_edition) { _, _ ->
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(EXTRA_SET_CODE, candidate.option.setCode)
                )
                finish()
            }
            .setNegativeButton(R.string.edition_scan_back) { _, _ -> showResult(result) }
            .show()
    }

    private fun showFailure(result: CardIdentificationResult? = null) {
        debug.visibility = View.VISIBLE
        status.text = if (result == null || result.borderSampleCount == 0) {
            getString(R.string.edition_scan_no_match)
        } else {
            getString(
                R.string.edition_scan_no_match_with_evidence,
                (result.boundaryConfidence * 100).toInt().coerceIn(0, 100),
                borderLabel(result.detectedBorder),
                (result.detectedBorderConfidence * 100).toInt().coerceIn(0, 100),
                result.borderSampleCount
            )
        }
    }

    private fun recycleUnusedResultBitmaps(result: CardIdentificationResult) {
        val displayed = result.analysisPreview ?: result.setSymbolCrop
        result.analysisPreview?.takeIf { it !== displayed && !it.isRecycled }?.recycle()
        result.setSymbolCrop?.takeIf { it !== displayed && !it.isRecycled }?.recycle()
    }

    private fun similarity(distance: Double): Int =
        ((1.0 - distance) * 100.0).toInt().coerceIn(0, 100)

    private fun borderMatchLabel(matches: Boolean?): String = when (matches) {
        true -> getString(R.string.edition_scan_border_matches)
        false -> getString(R.string.edition_scan_border_differs)
        null -> getString(R.string.edition_scan_border_unresolved)
    }

    private fun borderLabel(color: CardBorderColor): String = when (color) {
        CardBorderColor.BLACK -> getString(R.string.edition_scan_border_black)
        CardBorderColor.WHITE -> getString(R.string.edition_scan_border_white)
        CardBorderColor.GOLD -> getString(R.string.edition_scan_border_gold)
        CardBorderColor.SILVER -> getString(R.string.edition_scan_border_silver)
        CardBorderColor.MIXED -> getString(R.string.edition_scan_border_mixed)
        CardBorderColor.UNKNOWN -> getString(R.string.edition_scan_border_unknown)
    }

    private fun borderRgbSummary(result: CardIdentificationResult): String =
        CardBorderSide.entries.joinToString("\n") { side ->
            val zones = result.borderZones.filter { it.side == side }
            val red = zones.map { it.red }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
            val green = zones.map { it.green }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
            val blue = zones.map { it.blue }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
            "${borderSideLabel(side)}: $red, $green, $blue"
        }

    private fun borderSideLabel(side: CardBorderSide): String = when (side) {
        CardBorderSide.TOP -> getString(R.string.edition_scan_side_top)
        CardBorderSide.RIGHT -> getString(R.string.edition_scan_side_right)
        CardBorderSide.BOTTOM -> getString(R.string.edition_scan_side_bottom)
        CardBorderSide.LEFT -> getString(R.string.edition_scan_side_left)
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
