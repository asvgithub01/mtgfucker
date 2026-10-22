package io.asv.mtgocr.ocrreader.architecture

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import io.asv.mtgocr.ocrreader.domain.catalog.SetSummary
import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow
import io.asv.mtgocr.ocrreader.feature.catalog.*
import io.asv.mtgocr.ocrreader.feature.carddetail.CollectionCardDetail
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CatalogComposeTest {
    @get:Rule val compose = createComposeRule()
    private val sets = listOf(SetSummary("AAA", "Same name", "2024-01-01", 15),
        SetSummary("BBB", "Same name", "2023-01-01", 20))

    @Test fun queryAndSelectionAreExplicitEventsByCode() {
        var query = ""
        var selected = ""
        compose.setContent {
            MtgTheme(0xFF245B43.toInt(), 0xFFE0B64B.toInt(), 0xFFF6F1E7.toInt()) {
                SetCatalogScreen(SetCatalogUiState(loading = false, sets = sets),
                    { query = it }, { selected = it }, {}, {}, {})
            }
        }
        compose.runOnIdle { assertEquals("", selected); assertEquals("", query) }
        compose.onNodeWithTag("set-BBB").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("BBB", selected) }
        compose.onNodeWithTag("catalog-search").performScrollTo().performTextInput("AAA")
        compose.runOnIdle { assertEquals("AAA", query) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val shot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.cacheDir, "p4-catalog.png").outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        shot.recycle()
    }

    @Test fun errorHidesStaleRowsAndDialogAndOffersRetry() {
        var retries = 0
        compose.setContent {
            SetCatalogScreen(SetCatalogUiState(loading = false, failed = true, sets = sets, selected = sets[0]),
                {}, {}, {}, { retries++ }, {})
        }
        compose.onNodeWithTag("set-AAA").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(io.asv.mtgocr.ocrreader.R.string.launcher_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun largeFontAndSmallViewportKeepLastSetReachable() {
        val many = (1..30).map { SetSummary("SET$it", "Edition $it", "2025", it) }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                Box(Modifier.size(320.dp, 480.dp)) {
                    SetCatalogScreen(SetCatalogUiState(loading = false, sets = many), {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("set-catalog").performScrollToKey("set:SET30")
        compose.onNodeWithTag("set-SET30").assertIsDisplayed()
    }

    @Test fun detailRebindingCannotKeepPreviousPrintingPriceOrArtwork() {
        var row by mutableStateOf(CollectionRow("first", "First", "uuid-first", "foil", "pt", "good", 3,
            "first-art", listOf("Group"), listOf("Deck"), listOf("Deck"),
            storedPrice = "123.45 EUR", description = "Saved rules", evidenceCount = 2))
        var artworkUrl: String? = null
        var closes = 0
        compose.setContent {
            CollectionCardDetail(row, { closes++ }, artwork = { url, _, _ ->
                SideEffect { artworkUrl = url }
            })
        }
        compose.onNodeWithText("123.45 EUR").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("first-art", artworkUrl)
            row = row.copy(id = "second", name = "Second", printingUuid = "uuid-second", imageUrl = null,
                storedPrice = "", description = "")
        }
        compose.onNodeWithText("123.45 EUR").assertDoesNotExist()
        compose.onNodeWithText("uuid-first").assertDoesNotExist()
        compose.onNodeWithText("uuid-second").assertExists()
        compose.runOnIdle { assertNull(artworkUrl) }
        compose.onNodeWithTag("detail-close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }
}
