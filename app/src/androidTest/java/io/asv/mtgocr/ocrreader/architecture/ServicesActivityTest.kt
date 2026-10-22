package io.asv.mtgocr.ocrreader.architecture

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.ServicesPreviewActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ServicesActivityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun recreationAndRefreshDoNotChangeLocalPreferencesOrCollections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun inventory() = context.filesDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".Json") || it.name.endsWith(".Json.bak") || it.name.startsWith("photoScans.bin") }
            .associate { it.name to it.readBytes().toList() }
        fun settings() = listOf("library_catalog", "premium_access", "cloud_library_sync", "price_currency_preferences")
            .associateWith { context.getSharedPreferences(it, 0).all.toMap() }
        val filesBefore = inventory(); val prefsBefore = settings()
        ActivityScenario.launch(ServicesPreviewActivity::class.java).use { scenario ->
            compose.waitUntil(10000) { compose.onAllNodesWithTag("service-library-default").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("services-refresh").performClick()
            scenario.recreate()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("service-library-default").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            val shot = instrumentation.uiAutomation.takeScreenshot()
            File(context.cacheDir, "p6-services.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            shot.recycle()
        }
        assertEquals(filesBefore, inventory()); assertEquals(prefsBefore, settings())
    }
}
