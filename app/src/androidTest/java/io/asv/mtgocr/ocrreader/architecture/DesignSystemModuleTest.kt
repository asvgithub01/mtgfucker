package io.asv.mtgocr.ocrreader.architecture

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.asv.mtgocr.ocrreader.core.designsystem.LoadStatus
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesignSystemModuleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun themeKeepsInjectedPaletteAcrossModuleBoundary() {
        compose.setContent {
            MtgTheme(0xff123456.toInt(), 0xff234567.toInt(), 0xff345678.toInt()) {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    assertEquals(Color(0xff123456), colors.primary)
                    assertEquals(Color(0xff234567), colors.secondary)
                    assertEquals(Color(0xff345678), colors.background)
                    assertEquals(colors.background, colors.surface)
                }
            }
        }
        compose.waitForIdle()
    }
    @Test fun reusableStatusOnlyRetriesOnEnabledExplicitClick() {
        var loading by mutableStateOf(true)
        var error by mutableStateOf<String?>("Failure")
        var retries = 0
        compose.setContent { LoadStatus(loading, error, "Retry", { retries++ }) }
        compose.onNodeWithText("Failure").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, retries); loading = false }
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries); error = null }
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText("Failure").assertDoesNotExist()
    }
}
