package io.asv.mtgocr.ocrreader.architecture

import io.asv.mtgocr.ocrreader.app.ui.legacyLauncherBranding
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import io.asv.mtgocr.ocrreader.domain.launcher.*
import io.asv.mtgocr.ocrreader.feature.launcher.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class LauncherComposeTest {
    @get:Rule val compose = createComposeRule()
    private val data = LauncherData(listOf(LauncherLibrary("a", "Alpha"), LauncherLibrary("b", "Beta")), "a", null)

    @Test fun selectionAndOpenAreExplicitEventsNotRecompositionEffects() {
        var state by mutableStateOf(LauncherUiState(data, loading = false))
        var selected = ""
        var opens = 0
        compose.setContent {
            MtgTheme(0xFF245B43.toInt(), 0xFFE0B64B.toInt(), 0xFFF6F1E7.toInt()) {
                LauncherScreen(state, { selected = it }, { opens++ }, {}, branding = legacyLauncherBranding(), artwork = { _, _ -> })
            }
        }
        compose.onNodeWithTag("library-picker").performClick()
        compose.onNodeWithText("Beta").performClick()
        compose.runOnIdle { assertEquals("b", selected); assertEquals(0, opens) }
        compose.onNodeWithTag("open-library").performClick()
        compose.runOnIdle { assertEquals(1, opens); state = state.copy(data = data.copy(selectedId = "b")) }
        compose.onNodeWithTag("library-picker").assertTextContains("Beta")
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun unreadableOrLoadingCannotOpenAndSingleLibraryHidesPicker() {
        var state by mutableStateOf(LauncherUiState(data.copy(libraries = listOf(data.libraries.first())), loading = true))
        compose.setContent { LauncherScreen(state, {}, {}, {}, branding = legacyLauncherBranding(), artwork = { _, _ -> }) }
        compose.onNodeWithTag("open-library").assertIsNotEnabled()
        compose.onNodeWithTag("library-picker").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(loading = false, data = state.data!!.copy(readable = false)) }
        compose.onNodeWithTag("open-library").assertIsNotEnabled()
    }

    @Test fun smallViewportWithLargeFontKeepsActionReachable() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                Box(Modifier.size(320.dp, 480.dp)) {
                    LauncherScreen(LauncherUiState(data, loading = false), {}, {}, {}, branding = legacyLauncherBranding(), artwork = { _, _ -> })
                }
            }
        }
        compose.onNodeWithTag("open-library").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
}
