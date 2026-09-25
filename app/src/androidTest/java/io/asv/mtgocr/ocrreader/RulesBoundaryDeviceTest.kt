package io.asv.mtgocr.ocrreader
import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
class RulesBoundaryDeviceTest {
    @Test fun manualCornersAreNormalizedAndCopied() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = CardCropAdjustView(instrumentation.targetContext)
            val bitmap = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888)
            view.setPhoto(bitmap, arrayOf(PointF(140f,100f), PointF(644f,100f), PointF(644f,804f), PointF(140f,804f)))
            val points = checkNotNull(view.normalizedCorners())
            assertEquals(.175f, points[0].x, .0001f)
            assertEquals(804f/1100f, points[2].y, .0001f)
            points[0].x = 0f
            assertEquals(.175f, view.normalizedCorners()!![0].x, .0001f)
            view.clearPhoto()
            assertNull(view.normalizedCorners())
        }
    }
    @Test fun fixedFlagVisibleAndPresetNotPersistedAcrossActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("rules_scanner", 0)
        val fixed = if (prefs.contains("fixed_bounds")) prefs.getBoolean("fixed_bounds", false) else null
        val auto = if (prefs.contains("auto_unique_art")) prefs.getBoolean("auto_unique_art", true) else null
        prefs.edit().putBoolean("fixed_bounds", true).putBoolean("auto_unique_art", false).commit()
        try {
            androidx.test.core.app.ActivityScenario.launch(RulesScanActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val options = activity.findViewById<android.view.ViewGroup>(R.id.hashScanOptions)
                    val checkbox = (0 until options.childCount).map { options.getChildAt(it) }
                        .filterIsInstance<android.widget.CheckBox>()
                        .single { it.text.toString() == activity.getString(R.string.rules_fixed_bounds) }
                    assertTrue(checkbox.isChecked)
                    assertEquals(android.view.View.VISIBLE, checkbox.visibility)
                    val preset = RapidEditionScanActivity::class.java.getDeclaredField("rulesPreset").apply { isAccessible = true }
                    assertNull(preset.get(activity))
                }
            }
        } finally {
            val edit = prefs.edit()
            if (fixed == null) edit.remove("fixed_bounds") else edit.putBoolean("fixed_bounds", fixed)
            if (auto == null) edit.remove("auto_unique_art") else edit.putBoolean("auto_unique_art", auto)
            edit.commit()
        }
    }
    @Test fun automaticExperimentalDetectsPhysicalRectangle() {
        assertTrue(OpenCVLoader.initLocal())
        val bitmap = Bitmap.createBitmap(800,1100,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.LTGRAY)
        val paint = Paint().apply { color = Color.BLACK }
        canvas.drawRect(140f,100f,644f,804f,paint)
        paint.color = Color.WHITE; canvas.drawRect(158f,118f,626f,786f,paint)
        paint.color = Color.DKGRAY; canvas.drawRect(175f,180f,605f,470f,paint)
        try {
            val quad = checkNotNull(OpenCvCardDetector(true,true).detect(bitmap))
            assertEquals(140f / 800, quad.normalizedCorners[0].x, .012f)
            assertEquals(100f / 1100, quad.normalizedCorners[0].y, .012f)
            assertEquals(804f / 1100, quad.normalizedCorners[2].y, .012f)
        } finally { bitmap.recycle() }
    }
}
