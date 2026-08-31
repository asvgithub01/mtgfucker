package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrNameIndexTest {
    private fun alias(name: String) = CardNameAliasEntity(
        normalizedAlias = MtgJsonParsers.normalizeSearchName(name),
        canonicalName = name,
        displayName = name,
        language = "English",
        updatedAt = 1L
    )

    @Test fun toleratesSeveralErrorsInLongOldFrameNames() {
        val index = OcrNameIndex(listOf(alias("Swords to Plowshares"), alias("Serra Angel")))
        assertEquals("Swords to Plowshares", index.match(listOf("Svvords to Plowshare"))?.canonicalName)
        assertEquals("Serra Angel", index.match(listOf("Sera Ange1"))?.canonicalName)
    }

    @Test fun refusesAmbiguousFuzzyMatches() {
        val index = OcrNameIndex(listOf(alias("Firebolt"), alias("Fireball")))
        assertNull(index.match(listOf("Firebalt")))
    }
}
