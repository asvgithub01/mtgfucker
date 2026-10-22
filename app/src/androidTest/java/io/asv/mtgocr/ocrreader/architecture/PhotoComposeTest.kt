package io.asv.mtgocr.ocrreader.architecture

import io.asv.mtgocr.ocrreader.feature.photos.R as PhotosR

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.domain.photos.*
import io.asv.mtgocr.ocrreader.feature.photos.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PhotoComposeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun opensExactEvidenceAndDoesNotMixGlobalRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            PhotoScreen(PhotoUiState(false, PhotoSnapshot(
                PhotoSection(listOf(PhotoRecord("card:0", "Library card", null, "Saved evidence"))),
                PhotoSection(listOf(PhotoRecord("photo", "Global photo", null, "Stored status"))))), {}, {}) {
                Text("Image placeholder")
            }
        }
        compose.onNodeWithText("Global photo").assertDoesNotExist()
        compose.onNodeWithText("Library card").performClick()
        compose.onNodeWithText("Saved evidence").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val shot = instrumentation.uiAutomation.takeScreenshot()
        File(context.cacheDir, "p5-evidence.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shot.recycle()
        compose.onNodeWithText(context.getString(android.R.string.ok)).performClick()
        compose.onNodeWithText(context.getString(PhotosR.string.photo_preview_global)).performClick()
        compose.onNodeWithText("Library card").assertDoesNotExist()
        compose.onNodeWithText("Global photo").performClick()
        compose.onNodeWithText("Stored status").assertIsDisplayed()
        compose.onNodeWithText("Saved evidence").assertDoesNotExist()
    }
}
