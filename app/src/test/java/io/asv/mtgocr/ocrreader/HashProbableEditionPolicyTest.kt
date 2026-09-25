package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class HashProbableEditionPolicyTest {
    private fun variant(set: String, year: String = "2026", id: String = set) = ArtPrintingIndex.Variant(
        id, "00000000-0000-0000-0000-000000000001", "Card", "Card", "150",
        ArtPrintingIndex.SetInfo(set, set, "$year-01-01", set, null, null), "en", CardBorderColor.BLACK,
        ArtPrintingIndex.FINISH_NONFOIL, "rare", 0, null, null)
    private fun choose(rows: List<ArtPrintingIndex.Variant>, printing: PrintingMetadataGuess? = null,
                       symbol: String? = null, distances: Map<String, Double> = emptyMap()) =
        HashProbableEditionPolicy.choose(rows, printing, symbol, distances, "FUT", "150", "pt")

    @Test fun absentEvidenceUsesIndexedPrintingRatherThanFirstCatalogueRow() {
        assertEquals("FUT", choose(listOf(variant("TSR"), variant("FUT")))?.set?.code)
    }
    @Test fun yearAndUsableSymbolRankingOverrideIndexedFallback() {
        val rows = listOf(variant("FUT", "2007"), variant("TSR", "2021"))
        val year = PrintingMetadataGuess("2021", null, null, null, 2021, emptyList())
        assertEquals("TSR", choose(rows, year)?.set?.code)
        assertEquals("TSR", choose(rows, distances = mapOf("FUT" to .35, "TSR" to .26))?.set?.code)
        assertEquals("FUT", choose(rows, distances = mapOf("FUT" to Double.NaN, "TSR" to .8))?.set?.code)
    }
    @Test fun trustedFooterAndConfirmedSymbolAreConstraintsNotOptionalHints() {
        val rows = listOf(variant("FUT"), variant("TSR"))
        val footer = PrintingMetadataGuess("TSR", "150", "TSR", "pt", null, listOf("TSR"))
        assertEquals("TSR", choose(rows, footer)?.set?.code)
        assertNull(choose(rows, footer, "FUT"))
        assertNull(choose(rows, footer.copy(collectorNumber = "999")))
        assertNull(choose(rows, footer.copy(setCode = "SPG")))
        assertEquals("TSR", choose(rows, symbol = "TSR")?.set?.code)
    }
    @Test fun normalFinishPreferredAndLanguageRowsDoNotBiasRanking() {
        val normal = variant("FUT")
        val foil = normal.copy(printingUuid = "foil", finishes = ArtPrintingIndex.FINISH_FOIL)
        val chosen = choose(listOf(foil, normal, normal.copy(languageCode = "pt")))
        assertEquals("FUT", chosen?.printingUuid)
        assertEquals("pt", chosen?.languageCode)
    }
    @Test fun excludedHistoricalSetsAndMissingCandidatesNeverProduceChoice() {
        assertNull(choose(emptyList()))
        for (set in listOf("LEA", "LEB", "ARN", "ATQ", "LEG", "DRK")) assertNull(choose(listOf(variant(set))))
    }
}
