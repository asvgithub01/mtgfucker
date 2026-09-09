package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CardLanguageTest {
    @Test fun mapsMtgJsonNamesToScryfallCodes() {
        assertEquals("es", CardLanguage.toCode("Spanish"))
        assertEquals("pt", CardLanguage.toCode("Portuguese (Brazil)"))
        assertEquals("zhs", CardLanguage.toCode("Chinese Simplified"))
        assertEquals("zht", CardLanguage.toCode("Chinese Traditional"))
        assertEquals("phyrexian", CardLanguage.toCode("Phyrexian"))
    }

    @Test fun preservesKnownCodesAndRejectsUnknownLabels() {
        assertEquals("ja", CardLanguage.toCode("ja"))
        assertEquals("", CardLanguage.toCode("Not a language"))
    }
}
