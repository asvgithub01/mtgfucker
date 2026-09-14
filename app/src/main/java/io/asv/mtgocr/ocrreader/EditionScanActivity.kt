package io.asv.mtgocr.ocrreader

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Camera
import android.os.Bundle
import android.os.SystemClock
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
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
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

/** Focused second step: OCR supplies the name; this screen gathers visual edition evidence. */
class EditionScanActivity : AppCompatActivity() {
    private lateinit var preview: CameraSourcePreview
    private lateinit var liveGuide: View
    private lateinit var correction: CardCropAdjustView
    private lateinit var capture: Button
    private lateinit var cancel: Button
    private lateinit var instruction: TextView
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
    private var correctionMode = false
    private var analysisInFlight = false
    private val photoExecutor = Executors.newSingleThreadExecutor()

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
        liveGuide = findViewById(R.id.editionScanLiveGuide)
        correction = findViewById(R.id.editionCropCorrection)
        capture = findViewById(R.id.editionScanCapture)
        cancel = findViewById(R.id.editionScanCancel)
        instruction = findViewById(R.id.editionScanInstruction)
        debug = findViewById(R.id.editionScanDebug)
        crop = findViewById(R.id.editionScanCrop)
        status = findViewById(R.id.editionScanStatus)
        findViewById<TextView>(R.id.editionScanCardName).text =
            getString(R.string.edition_scan_title) + " · " + displayName
        cancel.setOnClickListener {
            if (correctionMode) returnToCamera() else finish()
        }
        capture.setOnClickListener {
            if (correctionMode) analyzeCorrectedPhoto() else takePhotoForCorrection()
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
                    val detected = bitmap?.let { CardQuadrilateralDetector.detect(it) }
                    val fallback = if (detected == null) {
                        bitmap?.let { CardFrameAnalyzer.analyze(it).bounds }
                    } else null
                    runOnUiThread {
                        if (isFinishing || isDestroyed) {
                            bitmap?.recycle()
                            return@runOnUiThread
                        }
                        if (bitmap == null || (detected == null && fallback == null)) {
                            capture.isEnabled = true
                            instruction.setText(R.string.edition_scan_align_card)
                            showFailure()
                        } else {
                            preview.stop()
                            if (detected != null) correction.setPhoto(bitmap, detected.corners)
                            else correction.setPhoto(bitmap, fallback!!)
                            correction.visibility = View.VISIBLE
                            liveGuide.visibility = View.GONE
                            correctionMode = true
                            cancel.setText(R.string.edition_scan_retake_photo)
                            capture.setText(R.string.edition_scan_analyze_crop)
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
            instruction.setText(R.string.edition_scan_align_card)
            showFailure()
        }
    }

    private fun analyzeCorrectedPhoto() {
        if (analysisInFlight) return
        val corrected = correction.extractCardBitmap()
        if (corrected == null) {
            instruction.setText(R.string.edition_scan_invalid_crop)
            return
        }
        analysisInFlight = true
        capture.isEnabled = false
        debug.visibility = View.VISIBLE
        status.setText(R.string.edition_scan_comparing)
        instruction.setText(R.string.edition_scan_comparing)
        replaceDebugBitmap(null)
        comparisonStartedAt = SystemClock.elapsedRealtime()
        photoExecutor.execute {
            val jpeg = ByteArrayOutputStream().use { output ->
                corrected.compress(Bitmap.CompressFormat.JPEG, 94, output)
                output.toByteArray()
            }
            corrected.recycle()
            repository.identifyCardArtwork(
                cardName = cardName,
                languageCode = language,
                jpeg = jpeg,
                lockedSetCodes = lockedSets,
                preferFoil = preferFoil,
                alreadyCropped = true
            ) { result, error ->
                analysisInFlight = false
                capture.isEnabled = true
                instruction.setText(R.string.edition_scan_adjust_corners)
                if (error != null || result.candidates.isEmpty()) {
                    replaceDebugBitmap(result.analysisPreview ?: result.setSymbolCrop)
                    recycleUnusedResultBitmaps(result)
                    showFailure(result, error)
                } else {
                    showResult(result)
                }
            }
        }
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
        instruction.setText(R.string.edition_scan_align_card)
        startCamera()
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
        AlertDialog.Builder(this)
            .setTitle(
                if (result.confident) R.string.edition_scan_confirmed
                else R.string.edition_scan_probable
            )
            .setAdapter(CandidateAdapter(result.candidates, result.detectedBorder)) { _, which ->
                showCandidateExplanation(result.candidates[which], result)
            }
            .setNegativeButton(R.string.edition_scan_adjust_crop) { _, _ -> resumeCropAdjustment() }
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

    private fun showFailure(
        result: CardIdentificationResult? = null,
        error: Throwable? = null
    ) {
        debug.visibility = View.VISIBLE
        status.text = if (result == null || result.borderSampleCount == 0) {
            if (error == null) getString(R.string.edition_scan_no_match)
            else getString(
                R.string.edition_scan_error_detail,
                error.message ?: error.javaClass.simpleName
            )
        } else {
            getString(
                R.string.edition_scan_no_match_with_evidence,
                (result.boundaryConfidence * 100).toInt().coerceIn(0, 100),
                borderLabel(result.detectedBorder),
                (result.detectedBorderConfidence * 100).toInt().coerceIn(0, 100),
                result.borderSampleCount,
                result.eligibleEditions,
                result.referenceImages,
                result.comparedImages,
                result.referenceFailures
            )
        }
        if (correctionMode && !isFinishing) {
            AlertDialog.Builder(this)
                .setTitle(R.string.edition_scan_no_match_title)
                .setMessage(status.text)
                .setPositiveButton(R.string.edition_scan_adjust_crop) { _, _ -> resumeCropAdjustment() }
                .setNegativeButton(R.string.edition_scan_retake_photo) { _, _ -> returnToCamera() }
                .show()
        }
    }

    private fun resumeCropAdjustment() {
        debug.visibility = View.GONE
        replaceDebugBitmap(null)
        instruction.setText(R.string.edition_scan_adjust_corners)
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

    /** Keeps the official collection symbol visible next to every proposed edition. */
    private inner class CandidateAdapter(
        private val candidates: List<CardIdentificationCandidate>,
        private val detectedBorder: CardBorderColor
    ) : BaseAdapter() {
        override fun getCount(): Int = candidates.size

        override fun getItem(position: Int): CardIdentificationCandidate = candidates[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.edition_scan_candidate_item, parent, false)
            val candidate = getItem(position)
            val symbol = view.findViewById<ImageView>(R.id.editionCandidateSetSymbol)
            view.findViewById<TextView>(R.id.editionCandidateTitle).text = getString(
                R.string.edition_scan_candidate_title,
                candidate.option.setName,
                candidate.option.setCode.uppercase(Locale.US)
            )
            view.findViewById<TextView>(R.id.editionCandidateScores).text = getString(
                R.string.edition_scan_candidate_scores,
                similarity(candidate.distance),
                similarity(candidate.artworkDistance),
                similarity(candidate.setSymbolDistance)
            )
            view.findViewById<TextView>(R.id.editionCandidateBorder).text = getString(
                R.string.edition_scan_candidate_borders,
                borderLabel(detectedBorder),
                borderLabel(candidate.referenceBorder),
                borderMatchLabel(candidate.borderMatches)
            )
            SetSymbolLoader.display(view.context, candidate.option.setCode, symbol)
            return view
        }
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
        if (!correctionMode) ensureCamera()
    }

    override fun onPause() {
        preview.stop()
        super.onPause()
    }

    override fun onDestroy() {
        replaceDebugBitmap(null)
        correction.clearPhoto()
        photoExecutor.shutdownNow()
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
}
