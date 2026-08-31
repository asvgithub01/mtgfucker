package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanPrintingPolicyTest {
    @Test fun prefersTheFirstNonFoilPrinting() {
        val options = listOf(option("foil-a", true), option("regular-a", false), option("regular-b", false))
        assertEquals("regular-a", ScanPrintingPolicy.preferred(options)?.printingUuid)
    }

    @Test fun fallsBackToTheFirstPrintingWhenAllAreFoil() {
        val options = listOf(option("foil-a", true), option("foil-b", true))
        assertEquals("foil-a", ScanPrintingPolicy.preferred(options)?.printingUuid)
    }

    @Test fun returnsNullForAnEmptyCandidateList() {
        assertNull(ScanPrintingPolicy.preferred(emptyList()))
    }

    private fun option(uuid: String, foil: Boolean) = CardEditionOption(
        printingUuid = uuid,
        cardName = "Card",
        displayName = "Card",
        setCode = "TST",
        setName = "Test",
        collectorNumber = "1",
        releaseDate = "2026-01-01",
        rarity = "common",
        finish = if (foil) "foil" else "nonfoil",
        isFoil = foil,
        imageUrl = null,
        typeLine = "",
        rulesText = "",
        price = null,
        currency = null,
        priceProvider = null,
        priceDate = null
    )
}
