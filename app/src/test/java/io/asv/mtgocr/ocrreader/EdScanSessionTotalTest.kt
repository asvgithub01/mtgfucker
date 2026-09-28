package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class EdScanSessionTotalTest {
    @Test fun countsCopiesWithoutTreatingMissingPricesAsFree() {
        val result = EdScanSessionTotal.calculate(listOf(2 to 3.5, 1 to null, 3 to 0.0, 1 to Double.NaN))
        assertEquals(7, result.copies); assertEquals(7.0, result.amount, .0001); assertEquals(2, result.unpricedCopies)
    }
    @Test fun includesMoreThanThirtyGalleryItemsAndEdits() {
        val rows = List(40) { 1 to 2.0 }
        assertEquals(80.0, EdScanSessionTotal.calculate(rows).amount, .0001)
        assertEquals(78.0, EdScanSessionTotal.calculate(rows.drop(1)).amount, .0001)
        assertEquals(0, EdScanSessionTotal.calculate(emptyList()).copies)
    }
}
