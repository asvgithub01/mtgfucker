package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class HashEditionResolutionPolicyTest {
    @Test fun uniqueArtworkResolvesWithoutSymbolAndKeepsPreferredLanguage() {
        val v = listOf(variant("spg", "SPG"), variant("spg", "SPG").copy(languageCode = "es"))
        assertEquals("es", HashEditionResolutionPolicy.uniqueArtwork(v, v, null, "es")?.languageCode)
        assertEquals("spg", HashEditionResolutionPolicy.uniqueArtwork(v, v, footer("SPG"), "en")?.printingUuid)
    }

    @Test fun uniquenessIsCheckedBeforeFilteringAndCannotOverrideContradictions() {
        val spg = listOf(variant("spg", "SPG"))
        assertNull(HashEditionResolutionPolicy.uniqueArtwork(spg + variant("stx", "STX"), spg, null, "en"))
        assertNull(HashEditionResolutionPolicy.uniqueArtwork(spg, emptyList(), null, "en"))
        assertNull(HashEditionResolutionPolicy.uniqueArtwork(spg, spg, footer("STX"), "en"))
        assertNull(HashEditionResolutionPolicy.uniqueArtwork(spg, spg, footer("SPG").copy(collectorNumber = "151"), "en"))
        assertNull(HashEditionResolutionPolicy.uniqueArtwork(emptyList(), spg, null, "en"))
    }

    @Test fun uniqueSetStillRequiresOneNormalPrintingAndPreservesHistoricalExclusions() {
        val normal = variant("normal", "SPG")
        val both = listOf(normal, normal.copy(printingUuid = "foil", finishes = ArtPrintingIndex.FINISH_FOIL))
        assertEquals("normal", HashEditionResolutionPolicy.uniqueArtwork(both, both, null, "en")?.printingUuid)
        val ambiguous = listOf(normal, normal.copy(printingUuid = "other", collectorNumber = "151"))
        assertNull(HashEditionResolutionPolicy.uniqueArtwork(ambiguous, ambiguous, null, "en"))
        val ancient = listOf(variant("old", "LEA"))
        assertNull(HashEditionResolutionPolicy.uniqueArtwork(ancient, ancient, null, "en"))
    }

    @Test fun catalogueUniquenessDoesNotMakeWeakOrContradictedArtworkRecognized() {
        assertTrue(HashEditionResolutionPolicy.recognizedArtwork("Archmage Emeritus", emptyList(), 10, 8))
        assertFalse(HashEditionResolutionPolicy.recognizedArtwork("Collective Defiance", emptyList(), 12, 28))
        assertFalse(HashEditionResolutionPolicy.recognizedArtwork("Collective Defiance", emptyList(), 8, 28))
        assertFalse(HashEditionResolutionPolicy.recognizedArtwork("Collective Defiance", listOf("Sarcomite Myr"), 4, 4))
        assertTrue(HashEditionResolutionPolicy.recognizedArtwork("Sarcomite Myr", listOf("Sarcomite Myr"), 14, 9))
    }

    @Test fun multipleCandidateArtworksOfSameCardRemainPrintingAlternatives() {
        val one = variant("one", "SPG")
        val two = variant("two", "SPG").copy(collectorNumber = "151")
        val unrelated = variant("other", "SPG").copy(cardName = "Unrelated card")
        val merged = HashEditionResolutionPolicy.identityVariants("Card", listOf(listOf(one), listOf(one, two), listOf(unrelated)))
        assertEquals(setOf("one", "two"), merged.map { it.printingUuid }.toSet())
        assertNull(HashEditionResolutionPolicy.automatic(merged, null, "SPG", "en"))
    }

    @Test fun ocrChoosesMyrEvenWhenWrongHashIsFirst() {
        assertEquals(1, HashEditionResolutionPolicy.identityIndex(listOf("Collective Defiance", "Sarcomite Myr"), listOf("Sarcomite Myr")))
        assertNull(HashEditionResolutionPolicy.identityIndex(listOf("Collective Defiance"), listOf("Sarcomite Myr")))
        assertNull(HashEditionResolutionPolicy.identityIndex(listOf("Sarcomite Myr"), emptyList()))
        assertNull(HashEditionResolutionPolicy.identityIndex(listOf("A", "B"), listOf("A", "B")))
    }

    @Test fun uniqueCachedPrintingAndYearAloneDoNotAuthorizeAddition() {
        val variants = listOf(variant("spg", "SPG"))
        assertNull(HashEditionResolutionPolicy.automatic(variants, null, null, "en"))
        assertNull(HashEditionResolutionPolicy.automatic(variants, PrintingMetadataGuess("2026", null, null, null, 2026, emptyList()), null, "en"))
    }

    @Test fun exactFooterResolvesWithoutSymbol() {
        val variants = listOf(variant("spg", "SPG"), variant("other", "STX"))
        assertEquals("spg", HashEditionResolutionPolicy.automatic(variants, footer("SPG"), null, "en")?.printingUuid)
    }

    @Test fun symbolRequiresUnambiguousPrintingAndNoFooterConflict() {
        val variants = listOf(variant("fut", "FUT"), variant("tsr", "TSR"))
        assertEquals("fut", HashEditionResolutionPolicy.automatic(variants, null, "FUT", "en")?.printingUuid)
        assertNull(HashEditionResolutionPolicy.automatic(variants, footer("TSR"), "FUT", "en"))
        assertNull(HashEditionResolutionPolicy.automatic(variants, footer("SPG"), null, "en"))
    }

    @Test fun severalPrintingsInSameSetRequireExplicitPrintingChoice() {
        val variants = listOf(variant("one", "SPG"), variant("two", "SPG").copy(collectorNumber = "151"))
        assertEquals(2, HashEditionResolutionPolicy.printings(variants, "SPG", "en").size)
        assertNull(HashEditionResolutionPolicy.automatic(variants, null, "SPG", "en"))
    }

    @Test fun languageRowsCollapseAndNormalFinishIsPreferred() {
        val normal = variant("normal", "9ED")
        val es = normal.copy(languageCode = "es")
        val foil = normal.copy(printingUuid = "foil", finishes = ArtPrintingIndex.FINISH_FOIL)
        val result = HashEditionResolutionPolicy.printings(listOf(normal, es, foil), "9ED", "es").single()
        assertEquals("normal", result.printingUuid)
        assertEquals("es", result.languageCode)
        assertEquals("nonfoil", result.preferredFinish())
    }

    @Test fun historicalSetsAreManualOnlyAndUnknownSetCannotBeChosen() {
        for (set in listOf("LEA", "LEB", "ARN", "ATQ", "LEG", "DRK")) {
            val v = listOf(variant("old", set))
            assertNull(HashEditionResolutionPolicy.automatic(v, footer(set), set, "en"))
            assertEquals("old", HashEditionResolutionPolicy.printings(v, set, "en").single().printingUuid)
            assertTrue(HashEditionResolutionPolicy.printings(v, "SPG", "en").isEmpty())
        }
    }

    private fun footer(set: String) = PrintingMetadataGuess("", "0150", set, "en", 2026, listOf(set))
    private fun variant(uuid: String, set: String) = ArtPrintingIndex.Variant(
        uuid, "00000000-0000-0000-0000-000000000001", "Card", "Card", "150",
        ArtPrintingIndex.SetInfo(set, set, "2026-01-01", set, null, null), "en", CardBorderColor.BLACK,
        ArtPrintingIndex.FINISH_NONFOIL, "rare", 0, null, null
    )
}
