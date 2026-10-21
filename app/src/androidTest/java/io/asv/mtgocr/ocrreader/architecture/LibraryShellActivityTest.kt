package io.asv.mtgocr.ocrreader.architecture

import android.graphics.Bitmap
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.MainActivity
import io.asv.mtgocr.ocrreader.OcrCaptureActivity
import io.asv.mtgocr.ocrreader.TripleScanActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class LibraryShellActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun previewRoundTripPreservesInventoryFiles() {
        fun inventory() = compose.activity.filesDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".Json") || it.name.endsWith(".Json.bak") }
            .associate { it.name to it.readBytes() }
        compose.waitUntil(15000) {
            runCatching { compose.onNodeWithTag("preview-library").assertIsEnabled(); true }.getOrDefault(false)
        }
        val before = inventory()
        compose.onNodeWithTag("preview-library").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("shell-grid").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tab-GROUPS").performClick()
        compose.waitForIdle()
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.cacheDir, "p3-shell.png").outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        shot.recycle()
        compose.onNodeWithTag("shell-home").performClick()
        compose.waitUntil(15000) {
            runCatching { compose.onNodeWithTag("open-library").assertIsEnabled(); true }.getOrDefault(false)
        }
        val after = inventory()
        assertEquals(before.keys, after.keys)
        before.forEach { (name, bytes) -> assertArrayEquals(name, bytes, after[name]) }
    }

    @Test fun legacyAndTripleReturnToSameShellWithoutLaunchingCamerasInTest() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val seen = CopyOnWriteArrayList<Intent>()
        val targets = setOf(OcrCaptureActivity::class.java.name, TripleScanActivity::class.java.name)
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.component?.className !in targets) return null
                seen += intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.waitUntil(15000) {
                runCatching { compose.onNodeWithTag("preview-library").assertIsEnabled(); true }.getOrDefault(false)
            }
            compose.onNodeWithTag("preview-library").performScrollTo().performClick()
            for ((index, tag) in listOf("shell-open-legacy", "shell-open-triple").withIndex()) {
                compose.waitUntil(15000) {
                    runCatching { compose.onNodeWithTag(tag).assertIsEnabled(); true }.getOrDefault(false)
                }
                compose.onNodeWithTag(tag).performScrollTo().performClick()
                compose.waitUntil(5000) { seen.size == index + 1 }
            }
            assertEquals(OcrCaptureActivity::class.java.name, seen[0].component!!.className)
            assertFalse(seen[0].getBooleanExtra("askStartupPriceRefresh", true))
            assertEquals(TripleScanActivity::class.java.name, seen[1].component!!.className)
            compose.onNodeWithTag("shell-grid").assertExists()
            compose.onNodeWithTag("shell-home").performScrollTo().performClick()
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
