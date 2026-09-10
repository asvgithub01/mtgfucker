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

    @Test fun ignoresLeadingCardFrameGlyphsAndFlattensAccents() {
        val index = OcrNameIndex(listOf(alias("Exile"), alias("Éowyn, Shieldmaiden")))

        assertEquals("Exile", index.match(listOf("(Exile"))?.canonicalName)
        assertEquals(
            "Éowyn, Shieldmaiden",
            index.match(listOf("(Eowyn, Shieldmaiden"))?.canonicalName
        )
    }

    @Test fun rejectsACompletelyDifferentShortWordBeforeALongSharedSuffix() {
        val index = OcrNameIndex(listOf(alias("Maga del espectáculo")))

        assertNull(index.match(listOf("cima del espectaculo")))
    }
}
