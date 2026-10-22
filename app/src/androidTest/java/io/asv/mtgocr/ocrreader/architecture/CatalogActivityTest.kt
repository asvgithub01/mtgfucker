package io.asv.mtgocr.ocrreader.architecture

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.SetCatalogPreviewActivity
import io.asv.mtgocr.ocrreader.data.CardDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CatalogActivityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realCatalogRecreationPreservesQueryInventoryAndOwnedPrintings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun inventory() = context.filesDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".Json") || it.name.endsWith(".Json.bak") }
            .associate { it.name to it.readBytes() }
        val before = inventory()
        val dao = CardDatabase.get(context).cardDao()
        val ownedBefore = dao.ownedPrintings().toSet()
        ActivityScenario.launch(SetCatalogPreviewActivity::class.java).use { scenario ->
            compose.waitUntil(90000) {
                compose.onAllNodesWithTag("catalog-ready").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithTag("catalog-failed").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("catalog-search").performScrollTo().performTextInput("AAA")
            scenario.recreate()
            compose.onNodeWithTag("catalog-search").assertTextContains("AAA")
            compose.onNodeWithTag("catalog-back").performScrollTo().assertIsDisplayed()
        }
        val after = inventory()
        assertEquals(before.keys, after.keys)
        before.forEach { (name, bytes) -> assertArrayEquals(name, bytes, after[name]) }
        assertEquals(ownedBefore, dao.ownedPrintings().toSet())
    }
}
