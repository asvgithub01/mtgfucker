package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class EditionSearchTest {
    @Test fun uppercaseSearchesCodes() {
        assertTrue(EditionSearch.matches("MKM", "Murders at Karlov Manor", "MK"))
        assertFalse(EditionSearch.matches("ABC", "MKM collection", "MKM"))
    }
    @Test fun namesIgnoreCaseAndAccents() {
        assertTrue(EditionSearch.matches("ABC", "Edición de prueba", "edicion de prueba"))
        assertTrue(EditionSearch.matches("ABC", "Edición de prueba", "Edición"))
    }
    @Test fun emptyQueryShowsAllAndLowercaseExactCodesStillWork() {
        assertTrue(EditionSearch.matches("MKM", "Murders at Karlov Manor", "  "))
        assertTrue(EditionSearch.matches("MKM", "Murders at Karlov Manor", "mkm"))
        assertFalse(EditionSearch.matches("MKM", "Murders at Karlov Manor", "unknown"))
    }
}
