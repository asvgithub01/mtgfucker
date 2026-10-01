package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerSetInputTest {
    @Test fun parsesMultipleSetsWithoutDuplicates() {
        assertEquals(setOf("STX", "SOS", "FRA"), ScannerSetInput.parse("stx, SOS  FRA, stx\n"))
        assertEquals(emptySet<String>(), ScannerSetInput.parse(" , \t"))
    }
}
