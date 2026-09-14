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
        assertEquals(2024, result.printingYear)
    }

    @Test
    fun normalizesLeadingZerosWhenMatchingCatalogNumber() {
        assertTrue(PrintingMetadataParser.collectorKeysMatch("0042", "42"))
        assertTrue(PrintingMetadataParser.collectorKeysMatch("001a", "1A"))
    }

    @Test
    fun repairsCommonOcrConfusionsInCollectorNumber() {
        val result = PrintingMetadataParser.parse(
            "O42/28I R\nMOM EN",
            setOf("MOM")
        )

        assertEquals("042", result.collectorNumber)
        assertEquals("MOM", result.setCode)
    }

    @Test
    fun recoversKnownSetCodeWithOneWrongCharacter() {
        val result = PrintingMetadataParser.parse(
            "123/281 R\nM0M EN",
            setOf("MOM", "ONE")
        )

        assertEquals("MOM", result.setCode)
        assertEquals("123", result.collectorNumber)
    }

    @Test
    fun repairsLetterOInsideCopyrightYear() {
        val result = PrintingMetadataParser.parse(
            "© 2O23 Wizards of the Coast\nLTR EN",
            setOf("LTR")
        )

        assertEquals(2023, result.printingYear)
        assertEquals("LTR", result.setCode)
    }
}
