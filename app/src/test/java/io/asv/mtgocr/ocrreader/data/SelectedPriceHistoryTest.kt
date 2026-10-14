package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.*
import org.junit.Test

class SelectedPriceHistoryTest {
    private fun quote(provider: String = "cardmarket", finish: String = "normal", amount: Double = 20.0,
        date: String = "2026-10-14", uuid: String = "one") = CardPriceEntity(uuid,finish,amount,"EUR",provider,date,1)
    @Test fun neverFallsBackToAnotherProvider() {
        assertNull(SelectedPrices.choose(listOf(quote("scryfall")),"cardmarket","one","nonfoil"))
    }
    @Test fun exactPrintingAndFinish() {
        assertNull(SelectedPrices.choose(listOf(quote(finish="foil")),"cardmarket","one","nonfoil"))
        assertNull(SelectedPrices.choose(listOf(quote()),"cardmarket","two","normal"))
        assertEquals(20.0,SelectedPrices.choose(listOf(quote()),"cardmarket","one","nonfoil")!!.amount,0.0)
    }
    @Test fun newestProviderDateWinsNotListOrder() {
        val old=quote(amount=128.0,date="2026-09-27")
        val fresh=quote(amount=27.39)
        assertEquals(fresh,SelectedPrices.choose(listOf(old,fresh),"cardmarket","one","normal"))
    }
    @Test fun invalidPricesAreNotZeroValuations() {
        for (value in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0))
            assertNull(SelectedPrices.choose(listOf(quote(amount=value)),"cardmarket","one","normal"))
        assertNotNull(SelectedPrices.choose(listOf(quote(amount=0.0)),"cardmarket","one","normal"))
    }
    private fun group() = GroupPriceHistoryEntity("id","library","group:deck","cardmarket",1,"EUR",100.0,0,"members")
    @Test fun groupComparisonsRequireSameMembershipCoverageCurrencyAndProvider() {
        val a=group()
        for (b in listOf(a.copy(composition="changed"),a.copy(missing=1),a.copy(currency="USD"),
            a.copy(provider="scryfall"),a.copy(library="other"),a.copy(scope="all")))
            assertFalse(PriceHistoryPolicy.comparable(a,b))
        assertTrue(PriceHistoryPolicy.comparable(a,a.copy(total=120.0,observedAt=2,id="new")))
    }
    @Test fun deltaHasSignAndPercentage() {
        assertEquals(20.0 to 20.0, PriceHistoryPolicy.delta(100.0,120.0))
        assertEquals(-20.0 to -20.0, PriceHistoryPolicy.delta(100.0,80.0))
        assertEquals(0.0 to 0.0, PriceHistoryPolicy.delta(100.0,100.0))
    }
    @Test fun noFakePercentageForZeroOrMissingBaseline() {
        assertNull(PriceHistoryPolicy.delta(null,10.0))
        assertEquals(10.0 to null, PriceHistoryPolicy.delta(0.0,10.0))
    }
}
