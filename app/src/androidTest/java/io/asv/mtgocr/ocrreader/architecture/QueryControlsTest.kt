package io.asv.mtgocr.ocrreader.architecture

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.asv.mtgocr.ocrreader.core.designsystem.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QueryControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingDisablesOnlyRefreshAndCompositionDoesNotInvokeActions() {
        var backs = 0; var refreshes = 0
        var loading by mutableStateOf(true)
        compose.setContent {
            QueryActions("Back", "Refresh", loading, { backs++ }, { refreshes++ }, Modifier.testTag("refresh"))
        }
        compose.runOnIdle { assertEquals(0, backs); assertEquals(0, refreshes) }
        compose.onNodeWithTag("refresh").assertIsNotEnabled()
        compose.onNodeWithText("Back").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, backs); loading = false }
        compose.onNodeWithTag("refresh").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun recompositionUsesCurrentCallbacks() {
        var first = 0; var second = 0
        var replaced by mutableStateOf(false)
        compose.setContent {
            QueryActions("Back", "Refresh", false, {}, if (replaced) ({ second++ }) else ({ first++ }))
        }
        compose.onNodeWithText("Refresh").performClick()
        compose.runOnIdle { replaced = true }
        compose.onNodeWithText("Refresh").performClick()
        compose.runOnIdle { assertEquals(1, first); assertEquals(1, second) }
    }

    @Test fun detailContentScrollsAndDismissalRemainsOwnedByCaller() {
        var dismissals = 0
        var open by mutableStateOf(true)
        compose.setContent {
            if (open) QueryDetailDialog("Details", "Close", { dismissals++; open = false }) {
                repeat(50) { Text("Line $it") }
            }
        }
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.onNodeWithText("Line 49").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Details").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }
}
