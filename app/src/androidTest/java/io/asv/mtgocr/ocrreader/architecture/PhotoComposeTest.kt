package io.asv.mtgocr.ocrreader.architecture

import io.asv.mtgocr.ocrreader.feature.photos.R as PhotosR

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.domain.photos.*
import io.asv.mtgocr.ocrreader.feature.photos.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PhotoComposeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectedImportAndTabSurviveSavedInstanceStateRestoration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PhotoScreen(PhotoUiState(false, PhotoSnapshot(
                PhotoSection(listOf(PhotoRecord("same-id", "Library item", null, "Library details"))),
                PhotoSection(listOf(PhotoRecord("same-id", "Imported item", null, "Import details"))))), {}, {}) {
                Text("Image placeholder")
            }
        }
        compose.onNodeWithText(context.getString(PhotosR.string.photo_preview_global)).performClick()
        compose.onNodeWithText("Imported item").performClick()
        compose.onNodeWithText("Import details").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Import details").assertIsDisplayed()
        compose.onNodeWithText("Library details").assertDoesNotExist()
        compose.onNodeWithText(context.getString(android.R.string.ok)).performClick()
        compose.onNodeWithText("Imported item").assertIsDisplayed()
        compose.onNodeWithText("Library item").assertDoesNotExist()
    }

    @Test fun selectedImportSurvivesTabLayoutChangeAndRestoration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var scale by mutableStateOf(1f)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                Box(Modifier.width(320.dp)) {
                    PhotoScreen(PhotoUiState(false, PhotoSnapshot(
                        PhotoSection(listOf(PhotoRecord("same", "Library item", null, "Library details"))),
                        PhotoSection(listOf(PhotoRecord("same", "Imported item", null, "Import details"))))),
                        {}, {}) { Text("Image placeholder") }
                }
            }
        }
        val global = context.getString(PhotosR.string.photo_preview_global)
        compose.onNodeWithText(global).performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Imported item").performClick()
        compose.runOnIdle { scale = 2f }
        compose.onNodeWithText("Import details").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Import details").assertIsDisplayed()
        compose.onNodeWithText("Library details").assertDoesNotExist()
        compose.onNodeWithText(context.getString(android.R.string.ok)).performClick()
        compose.onNodeWithText(global).performScrollTo().assertIsSelected()
        compose.runOnIdle { scale = 1f }
        compose.onNodeWithText(global).performScrollTo().assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Imported item").assertIsDisplayed()
        compose.onNodeWithText("Library item").assertDoesNotExist()
    }

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
