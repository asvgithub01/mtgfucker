package io.asv.mtgocr.ocrreader

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EdScanJobsDeviceTest {
    @Test fun savedPhotoRunsOfflineOcrAndKeepsReviewResultWithoutCollectionWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = android.graphics.Bitmap.createBitmap(400, 560, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        android.graphics.Canvas(bitmap).drawText("Draw a card", 30f, 200f, android.graphics.Paint().apply {
            textSize = 42f; color = android.graphics.Color.BLACK
        })
        val key = EdScanJobs.capture(context, bitmap, listOf(Corner(0f,0f),Corner(1f,0f),Corner(1f,1f),Corner(0f,1f)),
            "3f42c4d7-b555-449c-a539-119c1ae62232", "nonfoil")
        val dir = EdScanJobs.directory(context)
        val output = File(dir, "$key.ocr.json")
        try {
            val until = android.os.SystemClock.elapsedRealtime() + 90000
            while (!output.exists() && android.os.SystemClock.elapsedRealtime() < until) Thread.sleep(300)
            assertTrue("Persistent OCR must complete", output.exists())
            val result = org.json.JSONObject(output.readText())
            assertEquals("review_required", result.getString("status"))
            assertFalse(result.getBoolean("autoApplied"))
            assertEquals(4, result.getJSONArray("readings").length())
            assertTrue(File(dir, "$key.jpg").length() > 0)
            val original = output.readText()
            EdScanJobs.resume(context)
            Thread.sleep(500)
            assertEquals(original, output.readText())
        } finally {
            if (output.exists()) listOf(".jpg", ".json", ".ocr.json").forEach { File(dir, key+it).delete() }
        }
    }
    @Test fun deleteRequiresOneSecondAndCancelsOnRelease() {
        var count = 0
        ActivityScenario.launch(NativeCollectorVisionActivity::class.java).use { scenario ->
            lateinit var button: android.widget.ImageButton
            scenario.onActivity { activity ->
                val root = HoldToDelete.wrap(android.view.View(activity))
                activity.setContentView(root)
                HoldToDelete.bind(root, Runnable { count++ })
                button = root.findViewWithTag("hold_delete_card")
            }
            fun touch(action: Int) = scenario.onActivity {
                val now = android.os.SystemClock.uptimeMillis()
                val event = android.view.MotionEvent.obtain(now, now, action, 5f,5f,0)
                button.dispatchTouchEvent(event); event.recycle()
            }
            touch(android.view.MotionEvent.ACTION_DOWN)
            Thread.sleep(350)
            touch(android.view.MotionEvent.ACTION_UP)
            Thread.sleep(750)
            scenario.onActivity { assertEquals(0, count) }
            touch(android.view.MotionEvent.ACTION_DOWN)
            Thread.sleep(1150)
            touch(android.view.MotionEvent.ACTION_UP)
            scenario.onActivity { assertEquals(1, count) }
        }
    }
}
