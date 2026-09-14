package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun filterRemovesPrintingsThatDoNotExistInDetectedLanguage() {
        val options = listOf(
            option("4bb", "318", "English catalog name"),
            option("3ed", "247", "English catalog name"),
            option("4ed", "318", "English catalog name")
        )
        val spanish = listOf(
            LocalizedPrintingVariant("4ED", "318", "es", "Nombre ES", "https://img/4ed-es.jpg")
        )

        val filtered = LocalizedEditionPolicy.filter(options, spanish)

        assertEquals(listOf("4ed"), filtered.map { it.setCode })
        assertEquals("https://img/4ed-es.jpg", filtered.single().imageUrl)
        assertTrue(filtered.all { it.displayName == "Nombre ES" })
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
