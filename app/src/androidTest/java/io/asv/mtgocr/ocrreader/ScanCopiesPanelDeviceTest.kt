package io.asv.mtgocr.ocrreader

import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ScanCopiesPanelDeviceTest {
    @Test fun editionButtonChangesDisplayedSetWithoutChangingQuantityOrSaving() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Mtg)
            val panel = ScanCopiesPanel(context, "Undertaker", "Mercadian Masques (MMQ)", 1)
            panel.quantity.setText("4")
            var clicks = 0
            panel.edition.setOnClickListener {
                clicks++
                panel.showEdition("Time Spiral (TSB)")
            }
            panel.edition.performClick()
            assertEquals(1, clicks)
            assertTrue(panel.edition.text.contains("TSB"))
            assertEquals("4", panel.quantity.text.toString())
        }
    }
    @Test fun undertakerCatalogueKeepsSavedMercadianPrintingAcrossReorderedReprints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val index = ArtPrintingIndex.read(context.assets.open(ArtPrintingIndex.ASSET))
        val art = "d7fc5343-69dd-4c88-8b54-766f3ce0d361"
        val variants = index.variants(art).filter { it.cardName == "Undertaker" }
        assertTrue(variants.any { it.set.code in setOf("TSB", "TSR") })
        val original = HashEditionResolutionPolicy.printings(variants, "MMQ", "en").first()
        val state = ConsecutiveArtworkEdition()
        state.remember(art, original.printingUuid, "test-row")
        repeat(4) {
            state.observe(art)
            val uuid = state.retainedPrinting(art, variants.reversed().map { it.printingUuid })
            assertEquals(original.printingUuid, uuid)
            assertEquals("MMQ", variants.first { it.printingUuid == uuid }.set.code)
        }
    }

    @Test fun shippedAladdinCatalogResolvesNormalEnglishInsteadOfFoilUuid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val index = ArtPrintingIndex.read(context.assets.open(ArtPrintingIndex.ASSET))
        val variants = index.variants("4788d219-00e1-45ad-8ad1-fafd4ebc100c").filter { it.set.code == "9ED" }
        assertEquals(10, variants.size)
        assertEquals(2, variants.map { it.printingUuid }.distinct().size)
        val selected = checkNotNull(SetSymbolHashPolicy.selectVariant(variants, "9ED", "en"))
        assertEquals("22fc1065-f041-57cd-931f-1ceb4b9a5745", selected.printingUuid)
        assertEquals("en", selected.languageCode)
        assertEquals("286", selected.collectorNumber)
        assertEquals("nonfoil", selected.toEditionOption().finish)
        assertEquals(CardBorderColor.WHITE, selected.border)
    }
    @Test fun realQuantityControlsKeepFirstCopyAndAllowEditingFour() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, android.R.style.Theme_Material_Light)
            val panel = ScanCopiesPanel(context, "Aladdin’s Ring", "Ninth Edition (9ED)", 1)
            assertEquals("1", panel.quantity.text.toString())
            panel.minus.performClick()
            assertEquals("1", panel.quantity.text.toString())
            repeat(3) { panel.plus.performClick() }
            assertEquals("4", panel.quantity.text.toString())
            panel.minus.performClick()
            assertEquals("3", panel.quantity.text.toString())
            panel.quantity.setText("4")
            val state = RepeatedScanCopies().apply { added(RepeatedScanCopies.Key("id", "nonfoil", "en"), "row", 1) }
            assertEquals(3, state.additional(panel.quantity.text.toString()))
            panel.quantity.setText("2147483647")
            panel.plus.performClick()
            assertEquals("99", panel.quantity.text.toString())
        }
    }
}
