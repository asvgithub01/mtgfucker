package io.asv.mtgocr.ocrreader.architecture

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

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
}
