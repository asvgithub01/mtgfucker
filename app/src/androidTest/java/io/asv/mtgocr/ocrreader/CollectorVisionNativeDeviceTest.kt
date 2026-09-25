package io.asv.mtgocr.ocrreader

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.NativeCollectorVisionActivity
import io.asv.collectorvision.NativeCollectorVisionEngine
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CollectorVisionNativeDeviceTest {
    @Test fun nativeCameraProcessesAFrameAndReopens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        val prefs = context.getSharedPreferences("cornelius_scanner", 0)
        val existed = prefs.contains("show_thumbnail")
        val previous = prefs.getBoolean("show_thumbnail", true)
        prefs.edit().putBoolean("show_thumbnail", true).commit()
        try {
        ActivityScenario.launch(NativeCollectorVisionActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = NativeCollectorVisionActivity::class.java.getDeclaredField("start").apply { isAccessible = true }.get(activity) as android.widget.Button
                button.performClick()
            }
            fun waitForFrame() {
                val until = android.os.SystemClock.elapsedRealtime() + 20000
                var processed = false
                while (!processed && android.os.SystemClock.elapsedRealtime() < until) {
                    Thread.sleep(200)
                    scenario.onActivity { activity ->
                        processed = NativeCollectorVisionActivity::class.java.getDeclaredField("shownBitmap").apply { isAccessible = true }.get(activity) != null
                    }
                }
                assertTrue("CameraX must produce a successfully analyzed native frame", processed)
            }
            waitForFrame()
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<android.widget.CheckBox>("cornelius_show_thumbnail").performClick()
            }
            try {
                Thread.sleep(1800)
                scenario.onActivity { activity ->
                    val cls = NativeCollectorVisionActivity::class.java
                    assertNull(cls.getDeclaredField("shownBitmap").apply { isAccessible = true }.get(activity))
                    assertNotNull(cls.getDeclaredField("analysis").apply { isAccessible = true }.get(activity))
                    val status = cls.getDeclaredField("status").apply { isAccessible = true }.get(activity) as android.widget.TextView
                    assertTrue(status.text.toString().contains("ms"))
                }
            } finally {
                scenario.onActivity { activity ->
                    val check = activity.window.decorView.findViewWithTag<android.widget.CheckBox>("cornelius_show_thumbnail")
                    if (!check.isChecked) check.performClick()
                }
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                assertNull(NativeCollectorVisionActivity::class.java.getDeclaredField("analysis").apply { isAccessible = true }.get(activity))
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            waitForFrame()
        }
        } finally {
            val edit = prefs.edit()
            if (existed) edit.putBoolean("show_thumbnail", previous) else edit.remove("show_thumbnail")
            edit.commit()
        }
    }

    @Test fun nativeActivityIsPrivateAndCanCloseWithoutPreparingModels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(ComponentName(context, NativeCollectorVisionActivity::class.java), 0)
        assertFalse(info.exported)
        ActivityScenario.launch(NativeCollectorVisionActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                activity.finish()
            }
        }
    }

    /** Explicit device smoke: downloads pinned models/catalog if not cached. Does not write collection. */
    @Test fun realCpuModelsRunAndCatalogSearchReturnsFiniteCandidates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        NativeCollectorVisionEngine.prepare(context).use { engine ->
            val blank = Bitmap.createBitmap(640, 880, Bitmap.Config.ARGB_8888)
            blank.eraseColor(Color.WHITE)
            val result = engine.scan(blank)
            assertEquals(4, result.corners.size)
            assertTrue(result.corners.all { it.x.isFinite() && it.y.isFinite() })
            assertTrue(result.sharpness.isFinite())
            Log.i("CollectorVisionNativeTest", "blank present=${result.present} sharpness=${result.sharpness} detector=${result.detectionMs}")
            blank.recycle()
            // Optional private original supplied separately; never included in APK or source control.
            val sample = File(context.filesDir, "collectorvision-test.jpg")
            if (sample.exists()) {
                val bitmap = requireNotNull(BitmapFactory.decodeFile(sample.path))
                val scan = engine.scan(bitmap)
                Log.i("CollectorVisionNativeTest", "photo present=${scan.present} detector=${scan.detectionMs} recognition=${scan.recognitionMs} hits=${scan.hits.take(3)}")
                assertTrue("Physical-card fixture must be detected", scan.present)
                assertTrue("Real model and catalog must return candidates", scan.hits.isNotEmpty())
                assertTrue(scan.hits.all { it.score.isFinite() && it.cardId.isNotBlank() })
                bitmap.recycle()
            }
        }
    }
}
