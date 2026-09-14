package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintingMetadataParserTest {
    @Test
    fun parsesModernMultilineCollectorSetAndLanguage() {
        val result = PrintingMetadataParser.parse(
            "0123/0281 R\nMOM • EN\nJohn Avon",
            setOf("MOM", "ONE")
        )

        assertEquals("0123", result.collectorNumber)
        assertEquals("MOM", result.setCode)
        assertEquals("en", result.languageCode)
    }

    @Test
    fun recognizesPrintedJapaneseLanguageCode() {
        val result = PrintingMetadataParser.parse(
            "0042 M\nLTR · JP",
            setOf("LTR")
        )

        assertEquals("0042", result.collectorNumber)
        assertEquals("LTR", result.setCode)
        assertEquals("ja", result.languageCode)
    }

    @Test
    fun ignoresCopyrightYearAsStandaloneCollectorNumber() {
        val result = PrintingMetadataParser.parse(
            "© 2024 Wizards of the Coast\nMKM EN",
            setOf("MKM")
        )

        assertNull(result.collectorNumber)
        assertEquals("MKM", result.setCode)
    }

    @Test
    fun normalizesLeadingZerosWhenMatchingCatalogNumber() {
        assertTrue(PrintingMetadataParser.collectorKeysMatch("0042", "42"))
        assertTrue(PrintingMetadataParser.collectorKeysMatch("001a", "1A"))
    }
}
