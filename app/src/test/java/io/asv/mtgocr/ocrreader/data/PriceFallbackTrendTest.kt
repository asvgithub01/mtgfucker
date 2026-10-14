package io.asv.mtgocr.ocrreader.data

import io.asv.mtgocr.ocrreader.PriceTrendPolicy
import org.junit.Assert.*
import org.junit.Test

class PriceFallbackTrendTest {
    private fun quote(provider:String,amount:Double=10.0,finish:String="normal",date:String="2026-10-14") =
        CardPriceEntity("uuid",finish,amount,"EUR",provider,date,1)
    @Test fun missingFirstUsesNextNotCheapest() {
        assertEquals("scryfall",SelectedPrices.choose(listOf(quote("scryfall",12.0),quote("tcgplayer",1.0)),listOf("cardmarket","scryfall","tcgplayer"),"uuid","nonfoil")!!.provider)
    }
    @Test fun firstProviderWinsWhenPriceReturnsEvenIfOlder() {
        assertEquals("cardmarket",SelectedPrices.choose(listOf(quote("scryfall"),quote("cardmarket",40.0,date="2026-10-13")),listOf("cardmarket","scryfall"),"uuid","nonfoil")!!.provider)
    }
    @Test fun fallbackCannotSwitchFinish() {
        assertEquals("scryfall",SelectedPrices.choose(listOf(quote("cardmarket",finish="foil"),quote("scryfall")),listOf("cardmarket","scryfall"),"uuid","nonfoil")!!.provider)
        assertNull(SelectedPrices.choose(listOf(quote("cardmarket",finish="foil")),listOf("cardmarket"),"uuid","etched"))
    }
    @Test fun invalidPrimaryFallsBackButZeroIsValid() {
        assertEquals("scryfall",SelectedPrices.choose(listOf(quote("cardmarket",Double.NaN),quote("scryfall")),listOf("cardmarket","scryfall"),"uuid","normal")!!.provider)
        assertEquals(0.0,SelectedPrices.choose(listOf(quote("cardmarket",0.0),quote("scryfall")),listOf("cardmarket","scryfall"),"uuid","normal")!!.amount,0.0)
    }
    private fun point(date:String="2026-10-14",amount:Double=10.0) = PriceHistoryEntity("uuid","nonfoil","cardmarket",date,amount,"EUR",1)
    @Test fun graphNeverMixesProviderCurrencyPrintingOrFinish() {
        val a=point()
        val rows=listOf(a,a.copy(provider="scryfall"),a.copy(currency="USD"),a.copy(finish="foil"),a.copy(printingUuid="other"))
        assertEquals(1,PriceTrendPolicy.points(rows,"uuid","nonfoil","cardmarket","EUR").size)
    }
    @Test fun dailyPointsUseLatestObservationAndSortByDate() {
        val a=point();val corrected=a.copy(amount=12.0,observedAt=2)
        val points=PriceTrendPolicy.points(listOf(a,corrected,point("2026-10-13",15.0)),"uuid","nonfoil","cardmarket","EUR")
        assertEquals(listOf(15.0,12.0),points.map { it.amount })
        assertEquals(-3.0 to -20.0,PriceTrendPolicy.delta(points))
    }
    @Test fun oneObservationDoesNotInventTrend() {
        assertNull(PriceTrendPolicy.delta(listOf(PriceTrendPolicy.Point("2026-10-14",15.0))))
        assertNull(PriceTrendPolicy.delta(emptyList()))
    }
    @Test fun graphRejectsInvalidValuesAndDates() {
        val a=point()
        assertTrue(PriceTrendPolicy.points(listOf(a.copy(amount=Double.NaN),a.copy(priceDate="unknown"),a.copy(amount=-1.0)),"uuid","nonfoil","cardmarket","EUR").isEmpty())
    }
}
