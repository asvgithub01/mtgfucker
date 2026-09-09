package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalizedEditionPolicyTest {
    @Test
    fun portugueseThunderThrashElderMovesFromEnglishOnlyPrintingToAlara() {
        val options = listOf(
            option("pca", "53", "PCA English"),
            option("pc2", "53", "PC2 English"),
            option("ala", "117", "ALA English")
        )
        val portuguese = listOf(
            LocalizedPrintingVariant(
                "ALA", "117", "pt", "Ancião Surrador-do-Trovão", "https://img/ala-pt.jpg"
            )
        )

        val selected = LocalizedEditionPolicy.select(options, portuguese, "nonfoil", emptySet())

        assertEquals("ala", selected?.setCode)
        assertEquals("Ancião Surrador-do-Trovão", selected?.displayName)
        assertEquals("https://img/ala-pt.jpg", selected?.imageUrl)
    }

    @Test
    fun lockedEnglishOnlySetDoesNotSilentlyChooseAnotherEdition() {
        val selected = LocalizedEditionPolicy.select(
            listOf(option("pca", "53", "PCA English"), option("ala", "117", "ALA English")),
            listOf(LocalizedPrintingVariant("ALA", "117", "pt", "Nome PT", "https://img/pt.jpg")),
            "nonfoil",
            setOf("PCA")
        )

        assertNull(selected)
    }

    private fun option(set: String, collector: String, display: String) = CardEditionOption(
        printingUuid = "$set-$collector",
        cardName = "Thunder-Thrash Elder",
        displayName = display,
        setCode = set,
        setName = set.uppercase(),
        collectorNumber = collector,
        releaseDate = "2008-01-01",
        rarity = "uncommon",
        finish = "nonfoil",
        isFoil = false,
        imageUrl = "https://img/$set-en.jpg",
        typeLine = "Creature",
        rulesText = "Devour 3",
        price = 1.0,
        currency = "EUR",
        priceProvider = "test",
        priceDate = "2026-09-01"
    )
}
