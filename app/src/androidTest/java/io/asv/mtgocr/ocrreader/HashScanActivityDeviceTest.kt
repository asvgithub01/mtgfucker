package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.view.View
import android.widget.CheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HashScanActivityDeviceTest {
    @Test fun controlsAreVisibleAfterPreparation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(HashOnlyScanActivity::class.java).use { scenario ->
            val deadline = System.currentTimeMillis() + 120_000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    ready = activity.findViewById<View>(R.id.rapidScanLoading).visibility != View.VISIBLE
                }
                if (!ready) Thread.sleep(100)
            }
            assertTrue("Preparation did not finish", ready)
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<CheckBox>(R.id.hashScanOcr).isShown)
                assertTrue(activity.findViewById<CheckBox>(R.id.hashScanSymbol).isShown)
                assertTrue(activity.findViewById<View>(R.id.experimentalScanCapture).isShown)
                val root = activity.findViewById<View>(R.id.rapidScanRoot)
                val preview = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(preview))
                File(activity.cacheDir, "hash-scanner-ui.png").outputStream().use {
                    preview.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                preview.recycle()
            }
        }
    }
}
