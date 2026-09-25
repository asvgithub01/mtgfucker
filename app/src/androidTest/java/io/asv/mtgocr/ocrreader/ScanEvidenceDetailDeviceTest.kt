package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.bumptech.glide.Glide
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** No library writes or network: exercises the actual detail layout and local evidence image. */
class ScanEvidenceDetailDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun formatsLegacyAndV2EvidenceWithoutDumpingJson() {
        val v2 = ScanEvidenceFormatter.format(context, V2).toString()
        listOf("Giant Growth", "pHash 4 / dHash 7", "M10", "300", "0110", "Símbolo confirmado").forEach {
            assertTrue("Missing $it in $v2", v2.contains(it))
        }
        assertFalse(v2.contains("\"selectedPrinting\""))
        assertFalse(v2.contains("null"))
        val legacy = JSONObject(V2).apply { remove("symbolV2")
            put("symbol", JSONObject("""{"reliable":true,"distances":{"M10":0.2,"M11":0.5}}"""))
        }
        val v1 = ScanEvidenceFormatter.format(context, legacy.toString()).toString()
        assertTrue(v1.contains("Símbolo V1"))
        assertFalse(v1.contains("Símbolo confirmado"))
    }

    @Test fun handlesMalformedAndPartialMetadata() {
        assertEquals(context.getString(R.string.scan_debug_evidence_invalid),
            ScanEvidenceFormatter.format(context, "{broken").toString())
        val partial = ScanEvidenceFormatter.format(context, """{"ocr":{"rawTitle":["Card"]},"candidates":[null,{}]}""").toString()
        assertTrue(partial.contains("Card"))
        assertFalse(partial.contains("null"))
        assertTrue(partial.contains("—"))
    }

    @Test fun hidesPanelWithoutEvidenceAndDoesNotPairLatestMetadataWithOlderPhoto() {
        instrumentation.runOnMainSync {
            val root = layout()
            ScanEvidencePanel.bind(root, emptyList(), emptyList())
            assertEquals(View.GONE, root.findViewById<View>(R.id.scanEvidenceCard).visibility)
            ScanEvidencePanel.bind(root, listOf(V2, V2.replace("Giant Growth", "Latest Card")), listOf("scan_evidence/old.jpg"))
            root.findViewById<View>(R.id.scanEvidenceHeader).performClick()
            assertTrue(root.findViewById<TextView>(R.id.scanEvidenceMetadata).text.contains("Latest Card"))
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.scanEvidenceHistory).visibility)
            assertEquals(View.GONE, root.findViewById<View>(R.id.scanEvidencePhoto).visibility)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.scanEvidencePhotoUnavailable).visibility)
            val history = root.findViewById<Spinner>(R.id.scanEvidenceHistory)
            history.setSelection(1)
            history.onItemSelectedListener!!.onItemSelected(history, null, 1, 1)
            assertTrue(root.findViewById<TextView>(R.id.scanEvidenceMetadata).text.contains("Giant Growth"))
            root.findViewById<View>(R.id.scanEvidenceRawToggle).performClick()
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.scanEvidenceRaw).visibility)
            assertTrue(root.findViewById<TextView>(R.id.scanEvidenceRaw).text.contains("selectedPrinting"))
        }
    }

    @Test fun loadsPairedPhotoAndRejectsPathsOutsideEvidenceDirectory() {
        val directory = File(context.filesDir, "scan_evidence/detail-test-${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "card.jpg")
        val bitmap = Bitmap.createBitmap(315, 440, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(32, 64, 40))
            Canvas(this).drawText("Giant Growth", 20f, 50f, Paint().apply { color = Color.WHITE; textSize = 28f })
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val path = "scan_evidence/${directory.name}/card.jpg"
        var root: View? = null
        try {
            assertEquals(file.canonicalFile, ScanEvidencePanel.photoFile(context.filesDir, path))
            assertNull(ScanEvidencePanel.photoFile(context.filesDir, file.absolutePath))
            assertNull(ScanEvidencePanel.photoFile(context.filesDir, "../outside.jpg"))
            instrumentation.runOnMainSync {
                root = layout()
                ScanEvidencePanel.bind(root!!, listOf(V2), listOf(path))
                root!!.findViewById<View>(R.id.scanEvidenceHeader).performClick()
                // Give Glide a real measured target even without launching the catalog Activity.
                root!!.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY))
                root!!.layout(0, 0, 1080, 2200)
                // Detached views do not get Window pre-draw events; Glide resolves target size there.
                root!!.findViewById<ImageView>(R.id.scanEvidencePhoto).viewTreeObserver.dispatchOnPreDraw()
            }
            val deadline = System.currentTimeMillis() + 5000
            var loaded = false
            while (!loaded && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { loaded = root!!.findViewById<ImageView>(R.id.scanEvidencePhoto).drawable != null }
                if (!loaded) Thread.sleep(50)
            }
            assertTrue("Saved photo did not load", loaded)
            instrumentation.runOnMainSync {
                assertEquals(View.GONE, root!!.findViewById<View>(R.id.scanEvidencePhotoUnavailable).visibility)
                val panel = root!!.findViewById<View>(R.id.scanEvidenceCard)
                panel.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
                val preview = Bitmap.createBitmap(panel.width, panel.height, Bitmap.Config.ARGB_8888)
                Canvas(preview).apply { drawColor(Color.rgb(18, 29, 25)); panel.draw(this) }
                File(context.cacheDir, "scan-evidence-detail-test.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
                preview.recycle()
            }
        } finally {
            instrumentation.runOnMainSync { root?.findViewById<ImageView>(R.id.scanEvidencePhoto)?.let(Glide::clear) }
            file.delete()
            directory.delete()
        }
    }

    private fun layout(): View = LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_Mtg))
        .inflate(R.layout.activity_main2, null)

    companion object {
        private val V2 = """{
          "schemaVersion":2,"capturedAt":1790071200000,
          "selectedPrinting":{"uuid":"test-id","name":"Giant Growth","displayName":"Giant Growth","setCode":"M10","setName":"Magic 2010","collectorNumber":"184","finish":"nonfoil"},
          "checks":{"ocr":true,"symbol":false,"symbolV2":true,"autoAdd":true},
          "ocr":{"rawTitle":["Giant Growth"],"matchedNames":["Giant Growth"],"rawPrintingLine":"184 M10 EN","setCode":"M10","collectorNumber":"184","printingYear":2009,"setCandidates":["M10"]},
          "candidates":[{"name":"Giant Growth","phashDistance":4,"dhashDistance":7,"cachedVariants":20,"compatibleVariants":1,"resolvedPrintingUuid":"test-id","resolvedSetCode":"M10","resolvedCollectorNumber":"184","resolvedLanguage":"en"}],
          "symbolV2":{"selectedSet":"M10","reason":"simbolo_confirmado","elapsedMs":30,"maxDistance":0.24,"minMargin":0.055,"scores":[{"set":"M10","distance":0.1,"phashDistance":3,"silhouetteDistance":0.11,"cropXYWH":[500,490,30,24],"queryHash":"0110","referenceHash":"0100"}],"missingSets":[]},
          "language":{"code":"en","confidence":0.9,"recognizedText":"Target creature"},"effectiveLanguage":"en",
          "border":{"color":"BLACK","confidence":0.8,"sharpness":60,"glareRatio":0.01},
          "timingsMs":{"hash":10,"ocr":200,"symbol":30,"border":4,"language":50,"total":300},"errors":[]
        }""".trimIndent()
    }
}
