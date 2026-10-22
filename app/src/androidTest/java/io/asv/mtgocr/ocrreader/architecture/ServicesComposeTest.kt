package io.asv.mtgocr.ocrreader.architecture

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.asv.mtgocr.ocrreader.domain.services.*
import io.asv.mtgocr.ocrreader.feature.services.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ServicesComposeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun compositionDoesNotRefreshAndFailureHidesOldInventory() {
        var refreshes = 0
        var state by mutableStateOf(ServicesUiState(false, ServicesSnapshot(false, "EUR", true,
            listOf(LibraryFiles("default", "Test library", true, 20, null)))))
        compose.setContent { ServicesScreen(state, { refreshes++ }, {}) }
        compose.onNodeWithText("Test library").assertExists()
        compose.runOnIdle { assertEquals(0, refreshes) }
        compose.onNodeWithTag("services-refresh").performClick()
        compose.runOnIdle { assertEquals(1, refreshes); state = state.copy(failed = true) }
        compose.onNodeWithText("Test library").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }
    @Test fun lastLibraryRemainsReachableInLongList() {
        compose.setContent { ServicesScreen(ServicesUiState(false, ServicesSnapshot(true, "USD", false,
            (1..50).map { LibraryFiles("id$it", "Library $it", true, null, 200) })), {}, {}) }
        compose.onNodeWithTag("services-list").performScrollToKey("id50")
        compose.onNodeWithText("Library 50").assertIsDisplayed()
    }
}
