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

    @Test fun refusesToPromoteAReadFragmentToACompleteTitle() {
        val index = OcrNameIndex(listOf(alias("La Biblioplex"), alias("Sueños robados")))

        assertNull(index.match(listOf("biblioplex")))
        assertNull(index.match(listOf("suenos")))
    }

    @Test fun matchesCurrentSetSpanishNamesOnceLocalizedAliasesAreAvailable() {
        val index = OcrNameIndex(listOf(
            alias("Guardatomos de la Biblioplex"),
            alias("Diario de sueños")
        ))

        assertEquals(
            "Guardatomos de la Biblioplex",
            index.match(listOf("guarda tmons de la biblioplex"))?.canonicalName
        )
        assertEquals("Diario de sueños", index.match(listOf("diario de suenos"))?.canonicalName)
    }

    @Test fun completeTitleBeatsAnExactShortCardNameReportedInTheSameFrame() {
        val sanar = CardNameAliasEntity(
            normalizedAlias = "sanar genio sin terminar",
            canonicalName = "Sanar, Unfinished Genius // Wild Idea",
            displayName = "Sánar, genio sin terminar",
            language = "es",
            updatedAt = 1L
        )
        val index = OcrNameIndex(listOf(alias("Terminar"), sanar))

        assertEquals(
            sanar.canonicalName,
            index.match(listOf("terminar", "sanar genio sin terminar"))?.canonicalName
        )
    }

    @Test fun lockedSetNamesRejectAnExactCardFromAnotherSet() {
        val index = OcrNameIndex(listOf(alias("Terminar")))

        assertNull(index.match(listOf("terminar"), setOf("another canonical name")))
    }

    @Test fun matchesTheFrontTitleOfDoubleFacedCanonicalNames() {
        val smaug = alias("Smaug, the Great Calamity // Spew Flame")
        val beorn = alias("Beorn, Reluctant Host // Till and Tend")
        val index = OcrNameIndex(listOf(smaug, beorn))

        assertEquals(smaug.canonicalName, index.match(listOf("Smaug, the Great Calamity"))?.canonicalName)
        assertEquals(beorn.canonicalName, index.match(listOf("Beorn, Reluctant Host"))?.canonicalName)
    }
}
