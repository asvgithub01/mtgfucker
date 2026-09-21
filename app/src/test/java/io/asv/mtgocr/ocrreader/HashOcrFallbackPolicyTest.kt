package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardEditionOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HashOcrFallbackPolicyTest {
    @Test fun sameOcrNameRequiresTwoConflictingReads() {
        val first = HashOcrFallbackPolicy.observe(
            HashOcrConflictState(), listOf("Vines of Vastwood")
        )
        assertNull(HashOcrFallbackPolicy.confirmedName(first))

        val second = HashOcrFallbackPolicy.observe(first, listOf("Vines of Vastwood"))
        assertEquals("Vines of Vastwood", HashOcrFallbackPolicy.confirmedName(second))
    }

    @Test fun differentOcrNameRestartsConsensus() {
        val first = HashOcrConflictState("Vines of Vastwood", 1)
        val changed = HashOcrFallbackPolicy.observe(first, listOf("Giant Growth"))

        assertEquals(HashOcrConflictState("Giant Growth", 1), changed)
        assertNull(HashOcrFallbackPolicy.confirmedName(changed))
    }

    @Test fun exactSafePrintingWinsButHistoricalPrintingDoesNot() {
        val alpha = option("alpha", "LEA", "1", "1993-08-05")
        val zendikar = option("zen", "ZEN", "188", "2009-10-02")
        val modern = option("modern", "MM2", "168", "2015-05-22")

        assertEquals("zen", HashOcrFallbackPolicy.preferredPrinting(
            listOf(modern, alpha, zendikar),
            PrintingMetadataGuess("ZEN 188", "188", "ZEN", "en", 2009, listOf("ZEN"))
        )?.printingUuid)
        assertEquals("modern", HashOcrFallbackPolicy.preferredPrinting(
            listOf(alpha, modern),
            PrintingMetadataGuess("LEA 1", "1", "LEA", "en", 1993, listOf("LEA"))
        )?.printingUuid)
    }

    private fun option(uuid: String, set: String, collector: String, date: String) =
        CardEditionOption(
            uuid, "Vines of Vastwood", "Vines of Vastwood", set, set, collector,
            date, "common", "nonfoil", false, null, "", "", null, null, null, null
        )
}
