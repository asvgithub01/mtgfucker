package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HashPrintingVariantPolicyTest {
    @Test fun languageAndWhiteBorderResolveAnOldPrinting() {
        val black = variant("black", "3ED", "es", CardBorderColor.BLACK)
        val whiteEnglish = variant("white-en", "4ED", "en", CardBorderColor.WHITE)
        val whiteSpanish = variant("white-es", "4ED", "es", CardBorderColor.WHITE)

        val resolved = HashPrintingVariantPolicy.resolve(
            "Card", listOf(black, whiteEnglish, whiteSpanish), emptyList(), null,
            CardBorderColor.WHITE, "es", verifyBorder = true, verifyLanguage = true
        )

        assertEquals("white-es", resolved?.printingUuid)
    }

    @Test fun reusedArtRemainsUnresolvedWithoutEnoughEvidence() {
        val variants = listOf(
            variant("first", "3ED", "en", CardBorderColor.BLACK),
            variant("second", "5ED", "en", CardBorderColor.BLACK)
        )
        assertNull(HashPrintingVariantPolicy.resolve(
            "Card", variants, emptyList(), null, null, "",
            verifyBorder = false, verifyLanguage = false
        ))
    }

    @Test fun exactOcrSetAndCollectorCanResolveTheCachedPrinting() {
        val variants = listOf(
            variant("first", "3ED", "en", CardBorderColor.BLACK, "12"),
            variant("second", "5ED", "en", CardBorderColor.BLACK, "98")
        )
        val printing = PrintingMetadataGuess("", "098", "5ED", "en", 1997, listOf("5ED"))

        assertEquals("second", HashPrintingVariantPolicy.resolve(
            "Card", variants, listOf("Card"), printing, null, "en",
            verifyBorder = false, verifyLanguage = true
        )?.printingUuid)
    }

    @Test fun conflictingEvidenceNeverFallsBackToAnotherPrinting() {
        val variant = variant("only", "4ED", "en", CardBorderColor.WHITE)
        assertNull(HashPrintingVariantPolicy.resolve(
            "Card", listOf(variant), emptyList(), null,
            CardBorderColor.BLACK, "en", verifyBorder = true, verifyLanguage = true
        ))
    }

    private fun variant(
        uuid: String,
        setCode: String,
        language: String,
        border: CardBorderColor,
        collector: String = "1"
    ) = ArtPrintingIndex.Variant(
        printingUuid = uuid,
        scryfallId = "00000000-0000-0000-0000-000000000001",
        cardName = "Card",
        displayName = "Card",
        collectorNumber = collector,
        set = ArtPrintingIndex.SetInfo(setCode, setCode, "1995-01-01", setCode, null, null),
        languageCode = language,
        border = border,
        finishes = ArtPrintingIndex.FINISH_NONFOIL,
        rarity = "common",
        face = 0,
        mcmId = null,
        mcmMetaId = null
    )
}
