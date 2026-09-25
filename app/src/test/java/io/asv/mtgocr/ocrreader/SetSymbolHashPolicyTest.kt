package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class SetSymbolHashPolicyTest {
    @Test fun normalPrintingWinsOverSeparateFoilAndLanguageOrderDoesNotPickSpanish() {
        val normal = variant("normal", "9ED").copy(collectorNumber = "286", border = CardBorderColor.WHITE)
        val foil = normal.copy(printingUuid = "foil", collectorNumber = "286★", finishes = ArtPrintingIndex.FINISH_FOIL)
        val rows = listOf(foil, normal.copy(languageCode = "es"), normal)
        assertEquals(normal, SetSymbolHashPolicy.selectVariant(rows, "9ED", "en"))
        assertEquals("es", SetSymbolHashPolicy.selectVariant(rows, "9ED", "es")?.languageCode)
        assertNull(SetSymbolHashPolicy.selectVariant(rows + normal.copy(printingUuid = "other"), "9ED"))
        assertEquals(foil, SetSymbolHashPolicy.selectVariant(listOf(foil), "9ED"))
    }
    @Test fun retainedListSymbolComesFromOriginalCollectorNotCatalogLogo() {
        val list = variant("list", "PLST").copy(collectorNumber = "ZEN-193")
        assertEquals(mapOf("PLST" to listOf("ZEN")), SetSymbolHashPolicy.retainedSymbols(listOf(list)))
        assertEquals(mapOf("PLST" to emptyList<String>()), SetSymbolHashPolicy.retainedSymbols(
            listOf(list, list.copy(collectorNumber = "unknown"))))
    }

    @Test fun smallSymbolKeepsStrictThresholdButReliesMoreOnSilhouette() {
        val mm2 = SetSymbolHashPolicy.distance(.222222, 28, .028903)
        assertTrue(mm2 <= SetSymbolHashPolicy.MAX_DISTANCE)
        assertNull(SetSymbolHashPolicy.winner(mapOf("A" to .30, "B" to .6), emptyList()))
    }

    @Test fun clearWinnerRequiresGoodAbsoluteScoreAndMargin() {
        assertEquals("A", SetSymbolHashPolicy.winner(mapOf("A" to .10, "B" to .30), emptyList()))
        assertNull(SetSymbolHashPolicy.winner(mapOf("A" to .40, "B" to .90), emptyList()))
        assertNull(SetSymbolHashPolicy.winner(mapOf("A" to .10, "B" to .12), emptyList()))
    }

    @Test fun missingTemplatesCannotCreateAnArtificialWinner() {
        assertNull(SetSymbolHashPolicy.winner(mapOf("A" to .10, "B" to .30), listOf("C")))
        assertEquals("A", SetSymbolHashPolicy.winner(mapOf("A" to .10), emptyList()))
        assertNull(SetSymbolHashPolicy.winner(mapOf("A" to .50), emptyList()))
        assertNull(SetSymbolHashPolicy.winner(emptyMap(), emptyList()))
    }

    @Test fun identicalSymbolsAndNonFiniteScoresDoNotResolve() {
        assertNull(SetSymbolHashPolicy.winner(mapOf("A" to .1, "B" to .1), emptyList()))
        assertNull(SetSymbolHashPolicy.winner(mapOf("A" to .1, "B" to Double.NaN), emptyList()))
    }

    @Test fun symbolFollowsOcrConfirmedArtEvenWhenItIsNotTheTopHash() {
        assertEquals(1, SetSymbolHashPolicy.candidateIndex(listOf("Wrong", "Card"), listOf("Card"), true))
        assertNull(SetSymbolHashPolicy.candidateIndex(listOf("Wrong"), listOf("Card"), true))
        assertNull(SetSymbolHashPolicy.candidateIndex(listOf("Card"), emptyList(), true))
        assertEquals(0, SetSymbolHashPolicy.candidateIndex(listOf("Card"), emptyList(), false))
    }

    @Test fun absentOrConflictingSymbolNeverFallsBackToAnyPrinting() {
        val variants = listOf(variant("a", "M10"))
        assertNull(SetSymbolHashPolicy.selectVariant(variants, null))
        assertNull(SetSymbolHashPolicy.selectVariant(variants, "M11"))
        assertEquals("a", SetSymbolHashPolicy.selectVariant(variants, "m10")?.printingUuid)
    }

    @Test fun symbolCannotDistinguishTwoCollectorsWithinTheSameSet() {
        assertNull(SetSymbolHashPolicy.selectVariant(listOf(variant("a", "M10"), variant("b", "M10")), "M10"))
    }

    @Test fun multipleLanguagesOfOneUuidDoNotInventAnotherPrinting() {
        val a = variant("a", "M10")
        assertEquals("a", SetSymbolHashPolicy.selectVariant(listOf(a, a.copy(languageCode = "es")), "M10")?.printingUuid)
    }

    @Test fun historicalPreselectionRemainsProhibited() {
        for (code in listOf("LEA", "LEB", "ARN", "ATQ", "LEG", "DRK")) {
            assertNull(SetSymbolHashPolicy.selectVariant(listOf(variant("a", code)), code))
        }
    }

    private fun variant(uuid: String, code: String) = ArtPrintingIndex.Variant(
        printingUuid = uuid, scryfallId = "id", cardName = "Card", displayName = "Card",
        collectorNumber = "1", set = ArtPrintingIndex.SetInfo(code, code, "2009-01-01", code, null, null),
        languageCode = "en", border = CardBorderColor.BLACK,
        finishes = ArtPrintingIndex.FINISH_NONFOIL, rarity = "common", face = 0,
        mcmId = null, mcmMetaId = null
    )
}
