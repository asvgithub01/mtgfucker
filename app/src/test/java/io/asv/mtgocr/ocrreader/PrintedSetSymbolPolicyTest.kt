package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class PrintedSetSymbolPolicyTest {
    @Test fun coreSetCatalogueLogosAreNotPrintedSymbols() {
        for (set in listOf("LEA", "LEB", "2ED", "3ED", "4ED")) {
            assertTrue(PrintedSetSymbolPolicy.mayHaveNoSymbol(set))
            assertFalse(PrintedSetSymbolPolicy.needsPrintedReference(set))
        }
        for (set in listOf("ARN", "ATQ", "LEG", "DRK", "TMP", "M13", "6ED")) {
            assertFalse(PrintedSetSymbolPolicy.mayHaveNoSymbol(set))
            assertTrue(PrintedSetSymbolPolicy.needsPrintedReference(set))
        }
    }
    @Test fun fifthEditionKeepsChineseExceptionAndUnknownLanguage() {
        assertTrue(PrintedSetSymbolPolicy.mayHaveNoSymbol("5ed", "es"))
        assertFalse(PrintedSetSymbolPolicy.needsPrintedReference("5ed", "es"))
        assertFalse(PrintedSetSymbolPolicy.mayHaveNoSymbol("5ED", "zhs"))
        assertTrue(PrintedSetSymbolPolicy.needsPrintedReference("5ED", "zhs"))
        assertTrue(PrintedSetSymbolPolicy.mayHaveNoSymbol("5ED"))
        assertTrue(PrintedSetSymbolPolicy.needsPrintedReference("5ED"))
    }
}
