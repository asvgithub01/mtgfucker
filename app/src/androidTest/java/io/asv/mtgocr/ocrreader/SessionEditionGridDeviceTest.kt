package io.asv.mtgocr.ocrreader

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import org.junit.Assert.*
import org.junit.Test

/** UI tests without starting a camera, editing preferences or writing the user's collection. */
class SessionEditionGridDeviceTest {
    private fun option(id: String, set: String = "SPG", finish: String = "nonfoil") = CardEditionOption(
        id, "Card", "Card", set, "Set $set", id, "2026", "rare", finish, finish == "foil",
        null, "", "", null, null, null, null)

    @Test fun chooserShowsSymbolsWithoutScannerFlagAndDeliversOnlyOnExplicitClick() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Mtg)
            val selected = mutableListOf<CardEditionOption>()
            val panel = EditionGridContent(context, "nonfoil", selected::add, loadSymbol = { _, _ -> })
            panel.showOptions(listOf(option("one"), option("two", "STX")))
            val grid = panel.body.getChildAt(0) as HashEditionGridPanel
            assertEquals(setOf("SPG", "STX"), grid.tiles.keys)
            assertTrue(selected.isEmpty())
            grid.tiles.getValue("STX").performClick()
            grid.tiles.getValue("SPG").performClick()
            assertEquals(listOf("two"), selected.map { it.printingUuid })
        }
    }

    @Test fun multiplePrintingsNeedSecondChoiceAndBackReturnsToGrid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Mtg)
            val selected = mutableListOf<CardEditionOption>()
            val panel = EditionGridContent(context, "nonfoil", selected::add, loadSymbol = { _, _ -> })
            panel.showOptions(listOf(option("one"), option("two"), option("one", finish = "foil")))
            (panel.body.getChildAt(0) as HashEditionGridPanel).tiles.getValue("SPG").performClick()
            assertTrue(selected.isEmpty())
            var printingBody = panel.body.getChildAt(0) as LinearLayout
            assertEquals(2, (printingBody.getChildAt(1) as ListView).adapter.count)
            (printingBody.getChildAt(0) as Button).performClick()
            (panel.body.getChildAt(0) as HashEditionGridPanel).tiles.getValue("SPG").performClick()
            printingBody = panel.body.getChildAt(0) as LinearLayout
            val list = printingBody.getChildAt(1) as ListView
            list.performItemClick(View(context), 1, 1)
            list.performItemClick(View(context), 0, 0)
            assertEquals(listOf("two"), selected.map { it.printingUuid })
        }
    }

    @Test fun searchFiltersGridAndEmptyResultDoesNotSelectAnything() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Mtg)
            val panel = EditionGridContent(context, "nonfoil", { fail("No selection expected") }, loadSymbol = { _, _ -> })
            panel.showOptions(listOf(option("one"), option("two", "STX")))
            panel.search.setText("STX")
            assertEquals(setOf("STX"), (panel.body.getChildAt(0) as HashEditionGridPanel).tiles.keys)
            panel.search.setText("XYZ")
            assertTrue((panel.body.getChildAt(0) as HashEditionGridPanel).tiles.isEmpty())
            assertEquals(View.VISIBLE, panel.status.visibility)
        }
    }

    @Test fun rapidSessionRowHasAccessibleEditionButtonWithoutAnyFlag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Mtg)
            val row = LayoutInflater.from(context).inflate(R.layout.rapid_scan_session_item, null)
            val button = row.findViewById<View>(R.id.rapidSessionSetSymbol)
            assertEquals(View.VISIBLE, button.visibility)
            assertTrue(button.isClickable)
            assertTrue(button.isFocusable)
            assertEquals(context.getString(R.string.editions), button.contentDescription)
        }
    }
}
