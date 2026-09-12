package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCatalogTest {
    @Test
    fun libraryNamesAreSafeAndReadable() {
        assertEquals(
            "Ventas japonesas 2026",
            LibraryCatalog.cleanName("  Ventas\n\t japonesas   2026  ")
        )
    }

    @Test
    fun libraryNamesHaveABoundedLength() {
        assertEquals(60, LibraryCatalog.cleanName("a".repeat(90)).length)
    }
}
