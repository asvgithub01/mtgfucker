package io.asv.mtgocr.ocrreader.architecture

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.core.designsystem.QueryActions
import io.asv.mtgocr.ocrreader.domain.photos.*
import io.asv.mtgocr.ocrreader.domain.services.*
import io.asv.mtgocr.ocrreader.feature.photos.*
import io.asv.mtgocr.ocrreader.feature.services.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import io.asv.mtgocr.ocrreader.feature.photos.R as PhotosR

/** Semantics/layout checks only, not a TalkBack or contrast certification. No device settings changed. */
class QueryAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun NarrowLargeText(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
            MaterialTheme {
                Box(Modifier.width(320.dp).height(480.dp).testTag("visual-fixture")) { content() }
            }
        }
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onNodeWithTag("visual-fixture").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun actionsExposeButtonRolesLabelsAndMinimumTargetsAtLargeText() {
        var backs = 0; var refreshes = 0
        compose.setContent { NarrowLargeText {
            QueryActions("Inicio", "Actualizar", false, { backs++ }, { refreshes++ })
        } }
        for (label in listOf("Inicio", "Actualizar")) {
            compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHasClickAction().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        screenshot("query-a11y-actions.png")
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithText("Actualizar").performClick()
        compose.runOnIdle { assertEquals(1, backs); assertEquals(1, refreshes) }
    }

    @Test fun photoTabsExposeSelectionAndRemainUsableOnNarrowLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { NarrowLargeText {
            PhotoScreen(PhotoUiState(false, PhotoSnapshot(
                PhotoSection(listOf(PhotoRecord("same", "Library", null, "Evidence"))),
                PhotoSection(listOf(PhotoRecord("same", "Imported", null, "Imported details"))))), {}, {}) {
                Text("Image placeholder")
            }
        } }
        val library = context.getString(PhotosR.string.photo_preview_library)
        val global = context.getString(PhotosR.string.photo_preview_global)
        compose.onNodeWithText(library).performScrollTo().assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        compose.onNodeWithText(global).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(library).assertIsNotSelected()
        // Address the lazy item directly: the large header can exceed the viewport.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Imported").assertIsDisplayed()
        screenshot("query-a11y-photos.png")
        compose.onNodeWithText("Imported").performClick()
        compose.onNodeWithText("Imported details").assertIsDisplayed()
        compose.onNodeWithText("Evidence").assertDoesNotExist()
        compose.onNodeWithText(context.getString(android.R.string.ok)).assertHasClickAction().performClick()
        compose.onNodeWithText("Imported details").assertDoesNotExist()
    }

    @Test fun servicesLastEntryAndRefreshAreReachableAtLargeText() {
        var refreshed = 0
        compose.setContent { NarrowLargeText {
            ServicesScreen(ServicesUiState(false, ServicesSnapshot(true, "EUR", false,
                (1..30).map { LibraryFiles("id$it", "Library $it", true, 0, null) })), { refreshed++ }, {})
        } }
        compose.onNodeWithTag("services-list").performScrollToKey("id30")
        compose.onNodeWithText("Library 30").assertIsDisplayed()
        screenshot("query-a11y-services.png")
        compose.onNodeWithTag("services-list").performScrollToIndex(0)
        compose.onNodeWithTag("services-refresh").assertHasClickAction().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, refreshed) }
    }
}
