package io.asv.mtgocr.ocrreader.architecture

import io.asv.mtgocr.ocrreader.app.ui.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow
import io.asv.mtgocr.ocrreader.domain.library.*
import io.asv.mtgocr.ocrreader.feature.library.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryShellComposeTest {
    @get:Rule val compose = createComposeRule()
    private val row = CollectionRow("stable-id", "Test card", null, "foil", "es", "NM", 3,
        null, listOf("Group"), emptyList(), emptyList())

    @Test fun injectedDetailTracksStableIdentityAndDismissesWithoutWrites() {
        var state by mutableStateOf(LibraryShellUiState(loading = false, rows = listOf(row)))
        var shown: CollectionRow? = null
        var dismiss: (() -> Unit)? = null
        compose.setContent {
            LibraryShellScreen("Test", state, {}, {}, {}, {}, {}, {},
                destinations = LibraryDestinationLabels("Catalog", "Photos", "Services"),
                detail = { card, close -> SideEffect { shown = card; dismiss = close } },
                artwork = { _, _ -> })
        }
        compose.onNodeWithTag("shell-grid").performScrollToKey("card:stable-id")
        compose.onNodeWithTag("card-stable-id").performClick()
        compose.runOnIdle {
            assertEquals(row, shown)
            state = state.copy(rows = listOf(row.copy(id = "other"), row.copy(quantity = 7)))
        }
        compose.runOnIdle {
            assertEquals("stable-id", shown?.id)
            assertEquals(7, shown?.quantity)
            dismiss!!()
        }
        compose.runOnIdle { shown = null; state = state.copy(rows = listOf(row)) }
        compose.runOnIdle { assertNull(shown) }
    }

    @Test fun groupsAndTabsEmitEventsAndErrorsHideStaleCards() {
        var state by mutableStateOf(LibraryShellUiState(loading = false,
            options = LibraryOptions(section = LibrarySection.GROUPS),
            memberships = listOf(MembershipSummary("Group", 3, 0))))
        var membership: String? = null
        var section: LibrarySection? = null
        compose.setContent {
            LibraryShellScreen("Test", state, { section = it }, { membership = it }, {}, {}, {}, {},
                destinations = legacyLibraryDestinationLabels(),
                detail = { card, dismiss -> LegacyCollectionCardDetail(card, dismiss) }, artwork = { _, _ -> })
        }
        compose.onNodeWithTag("membership-Group").performClick()
        compose.runOnIdle { assertEquals("Group", membership) }
        compose.onNodeWithTag("tab-CARDS").performClick()
        compose.runOnIdle {
            assertEquals(LibrarySection.CARDS, section)
            state = LibraryShellUiState(loading = false, rows = listOf(row), copies = 3)
        }
        compose.onNodeWithTag("shell-grid").performScrollToKey("card:stable-id")
        compose.onNodeWithTag("card-stable-id").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(failure = ShellReadFailure.COLLECTION_UNREADABLE) }
        compose.onNodeWithTag("card-stable-id").assertDoesNotExist()
    }

    @Test fun smallViewportLargeFontCanReachCardsAndReadOnlyDetails() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                Box(Modifier.size(320.dp, 480.dp)) {
                    LibraryShellScreen("Test", LibraryShellUiState(loading = false, rows = listOf(row)),
                        {}, {}, {}, {}, {}, {}, destinations = legacyLibraryDestinationLabels(),
                detail = { card, dismiss -> LegacyCollectionCardDetail(card, dismiss) }, artwork = { _, _ -> })
                }
            }
        }
        compose.onNodeWithTag("shell-grid").performScrollToKey("card:stable-id")
        compose.onNodeWithTag("card-stable-id").assertIsDisplayed().performClick()
        compose.onNode(isDialog()).assertExists()
    }
}
