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

    @Test fun prefersFoilWhenRequested() {
        val options = listOf(option("regular-a", false), option("foil-a", true))
        assertEquals("foil-a", ScanPrintingPolicy.preferred(options, true)?.printingUuid)
    }

    @Test fun fallsBackToRegularWhenNoFoilExists() {
        val options = listOf(option("regular-a", false), option("regular-b", false))
        assertEquals("regular-a", ScanPrintingPolicy.preferred(options, true)?.printingUuid)
    }

    @Test fun singleEligibleAcceptsSeveralFinishesOfTheSamePrinting() {
        val options = listOf(option("same", true), option("same", false))
        assertEquals("same", ScanPrintingPolicy.singleEligible(options, emptySet(), false)?.printingUuid)
        assertEquals(false, ScanPrintingPolicy.singleEligible(options, emptySet(), false)?.isFoil)
    }

    @Test fun singleEligibleRejectsCardsWithSeveralPossiblePrintings() {
        assertNull(ScanPrintingPolicy.singleEligible(
            listOf(option("first", false), option("second", false)),
            emptySet(),
            false
        ))
    }

    @Test fun singleEligibleHonoursTheDetectedSetLock() {
        val options = listOf(
            option("first", false, "ONE"),
            option("second", false, "TWO")
        )
        assertEquals(
            "second",
            ScanPrintingPolicy.singleEligible(options, setOf("two"), false)?.printingUuid
        )
    }

    @Test fun affordableOnlyConsidersFiveNewestSets() {
        val options = (1..6).map { year -> option("id$year", false, "S$year")
            .copy(releaseDate = "202$year-01-01", price = year.toDouble()) }
        assertEquals("id2", ScanPrintingPolicy.recentAffordable(options, false)?.printingUuid)
    }
    @Test fun unknownPriceIsNotFreeAndFinishNeverFallsBack() {
        val unknown = option("unknown", false).copy(price = null)
        val known = option("known", false).copy(price = 2.0)
        assertEquals("known", ScanPrintingPolicy.recentAffordable(listOf(unknown, known), false)?.printingUuid)
        assertNull(ScanPrintingPolicy.recentAffordable(listOf(known), true))
    }
    @Test fun currencyConversionParticipatesInChoice() {
        val a = option("a", false).copy(price = 1.0)
        val b = option("b", false).copy(price = 2.0)
        assertEquals("b", ScanPrintingPolicy.recentAffordable(listOf(a,b), false) {
            if (it.printingUuid == "a") 3.0 else 2.0
        }?.printingUuid)
    }
    @Test fun noKnownPricesUseMostRecentWithDeterministicTie() {
        val old = option("a", false).copy(releaseDate = "2000-01-01")
        val newest = option("b", false).copy(releaseDate = "2026-01-01")
        assertEquals("b", ScanPrintingPolicy.recentAffordable(listOf(old,newest), false)?.printingUuid)
    }
    @Test fun invalidPricesCannotWin() {
        val options = listOf(option("a", false).copy(price = Double.NaN),
            option("b", false).copy(price = -1.0), option("c", false).copy(price = 5.0))
        assertEquals("c", ScanPrintingPolicy.recentAffordable(options, false)?.printingUuid)
    }

    private fun option(uuid: String, foil: Boolean, setCode: String = "TST") = CardEditionOption(
        printingUuid = uuid,
        cardName = "Card",
        displayName = "Card",
        setCode = setCode,
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
