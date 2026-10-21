package io.asv.mtgocr.ocrreader.architecture

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.MainActivity
import io.asv.mtgocr.ocrreader.OcrCaptureActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class LauncherActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recreationKeepsPromptConsumedAndLeavesScannerUnstarted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val seen = CopyOnWriteArrayList<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.component?.className != OcrCaptureActivity::class.java.name) return null
                seen += intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.waitUntil(15000) {
                runCatching { compose.onNodeWithTag("open-library").assertIsEnabled(); true }.getOrDefault(false)
            }
            val shot = instrumentation.uiAutomation.takeScreenshot()
            File(compose.activity.cacheDir, "launcher-compose.png").outputStream().use {
                shot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            shot.recycle()
            compose.onNodeWithTag("open-library").performClick()
            compose.waitUntil(5000) { seen.size == 1 }
            assertTrue(seen[0].getBooleanExtra("askStartupPriceRefresh", false))
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) {
                runCatching { compose.onNodeWithTag("open-library").assertIsEnabled(); true }.getOrDefault(false)
            }
            compose.onNodeWithTag("open-library").performClick()
            compose.waitUntil(5000) { seen.size == 2 }
            assertFalse(seen[1].getBooleanExtra("askStartupPriceRefresh", true))
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
