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

    @Test fun correctsNoisyCompleteLandTaxTitle() {
        val index = OcrNameIndex(listOf(alias("Land Tax"), alias("Lend")))

        assertEquals("Land Tax", index.match(listOf("lend Tar--"))?.canonicalName)
    }

    @Test fun joinsSplitOcrFragmentsBeforeChoosingAnExactShortName() {
        val index = OcrNameIndex(listOf(alias("Land Tax"), alias("Lend")))

        assertEquals("Land Tax", index.match(listOf("lend", "Tar--"))?.canonicalName)
    }

    @Test fun whiteFrameDisambiguatesExileFromExactBlueGuileOcr() {
        val colors = CardColorIndex.from(mapOf("Exile" to "W", "Guile" to "U"))
        val index = OcrNameIndex(listOf(alias("Exile"), alias("Guile")), colors)

        assertEquals("Guile", index.match(listOf("Guile"))?.canonicalName)
        assertEquals("Exile", index.match(listOf("Guile"), "W")?.canonicalName)
    }

    @Test fun colorAssistGivesShortOldFrameNamesOneExtraOcrError() {
        val colors = CardColorIndex.from(mapOf("Tariff" to "W", "Guile" to "U"))
        val index = OcrNameIndex(listOf(alias("Tariff"), alias("Guile")), colors)

        assertEquals("Tariff", index.match(listOf("Tarlf"), "W")?.canonicalName)
    }
}
