package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardPriceEntity
import org.junit.Assert.*
import org.junit.Test

class PriceOriginPolicyTest {
    private val input = PriceOriginPolicy.Input("fra403", "foil", 27.39, "EUR", 4)
    private val row = CardPriceEntity("fra403", "foil", 27.39, "EUR", "cardmarket", "2026-10-13", 0)

    @Test fun exactReferenceReportsProviderPriceDateNotRefreshTimestamp() {
        assertEquals(PriceOriginPolicy.Origin("cardmarket", "2026-10-13"),
            PriceOriginPolicy.matching(input, listOf(row.copy(updatedAt = 999999999))))
    }
    @Test fun old128EurosNeverClaimsDateOfNew27EuroPrice() {
        assertNull(PriceOriginPolicy.matching(input.copy(amount = 128.0), listOf(row)))
    }
    @Test fun printingFinishAndCurrencyMustAllMatch() {
        for (wrong in listOf(row.copy(printingUuid = "fra413"), row.copy(finish = "normal"), row.copy(currency = "USD")))
            assertNull(PriceOriginPolicy.matching(input, listOf(wrong)))
    }
    @Test fun normalAndNonfoilAreAliasesButEtchedIsNotFoil() {
        assertNotNull(PriceOriginPolicy.matching(input.copy(finish = "nonfoil"), listOf(row.copy(finish = "normal"))))
        assertNull(PriceOriginPolicy.matching(input.copy(finish = "etched"), listOf(row)))
    }
    @Test fun missingInvalidOrNegativePriceIsUnknown() {
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0))
            assertNull(PriceOriginPolicy.matching(input.copy(amount = value), listOf(row)))
        assertNull(PriceOriginPolicy.matching(input, emptyList()))
    }
    @Test fun ambiguousProviderOrDateIsNotInvented() {
        assertNull(PriceOriginPolicy.matching(input, listOf(row, row.copy(provider = "scryfall"))))
        assertNull(PriceOriginPolicy.matching(input, listOf(row, row.copy(priceDate = "2026-09-27"))))
        assertNotNull(PriceOriginPolicy.matching(input, listOf(row, row)))
    }
    @Test fun emptyProviderOrDateAndDifferentRoundedAmountAreUnknown() {
        for (wrong in listOf(row.copy(provider = ""), row.copy(priceDate = ""), row.copy(amount = 27.40)))
            assertNull(PriceOriginPolicy.matching(input, listOf(wrong)))
        assertNotNull(PriceOriginPolicy.matching(input, listOf(row.copy(amount = 27.394))))
    }
}
