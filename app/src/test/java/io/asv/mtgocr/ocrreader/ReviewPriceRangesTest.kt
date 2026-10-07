package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class ReviewPriceRangesTest {
    @Test fun editionsAndLanguagesAreComparedSeparately() {
        val result=ReviewPriceRanges.calculate(listOf(ReviewPriceRanges.Quote("A|1","en",1.0),ReviewPriceRanges.Quote("B|2","en",5.0),ReviewPriceRanges.Quote("A|1","ja",3.0)),4)
        assertEquals(4.0,result.editions!!.euros,0.0);assertEquals(400.0,result.editions.percent!!,0.0)
        assertEquals(2.0,result.languages!!.euros,0.0);assertEquals(3,result.priced);assertEquals(4,result.total)
        assertTrue(ReviewPriceRanges.matches(result,0,3.0,300.0));assertFalse(ReviewPriceRanges.matches(result,2,3.0,0.0))
    }
    @Test fun maximumPercentIsNotLostWhenAnotherGroupHasLargerEuroRange() {
        val r=ReviewPriceRanges.calculate(listOf(ReviewPriceRanges.Quote("a","en",10.0),ReviewPriceRanges.Quote("b","en",20.0),ReviewPriceRanges.Quote("c","ja",.1),ReviewPriceRanges.Quote("d","ja",1.0)),4)
        assertEquals(10.0,r.editions!!.euros,0.0);assertEquals(900.0,r.editions.percent!!,.0001)
    }
    @Test fun unavailableIsNotZeroAndCannotMatchThreshold() {
        assertNull(ReviewPriceRanges.spread(listOf(2.0)))
        assertNull(ReviewPriceRanges.spread(listOf(0.0,5.0))!!.percent)
        assertFalse(ReviewPriceRanges.matches(null,0,1.0,0.0));assertTrue(ReviewPriceRanges.matches(null,0,0.0,0.0))
    }
}
