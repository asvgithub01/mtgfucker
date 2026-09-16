package io.asv.mtgocr.ocrreader.data

import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardmarketCsvExporterTest {
    @Test
    fun groupsBySetAndSplitsEveryHundredRows() {
        val cards = (1..101).map { card("Card $it", "eoe") } + card("Other set", "mkm")
        val prices = cards.associate { it.collectionItemId to 1.25 }

        val plan = CardmarketCsvExporter.prepare(cards, prices)

        assertEquals(listOf(100, 1, 1), plan.batches.map { it.rows.size })
        assertEquals(listOf("EOE", "EOE", "MKM"), plan.batches.map { it.setCode })
        assertEquals(listOf(1, 2, 1), plan.batches.map { it.number })
        assertEquals(listOf(2, 2, 1), plan.batches.map { it.total })
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun skipsRowsThatCouldBeUnsafeToList() {
        val valid = card("Valid", "eoe")
        val missingLanguage = card("Unknown language", "eoe").apply { languageCode = "" }
        val unsupportedLanguage = card("Unsupported", "eoe").apply { languageCode = "la" }
        val missingSet = card("No set", "")
        val cards = listOf(valid, missingLanguage, unsupportedLanguage, missingSet)

        val plan = CardmarketCsvExporter.prepare(cards, cards.associate { it.collectionItemId to 2.0 })

        assertEquals(1, plan.eligibleCount)
        assertEquals(3, plan.skippedCount)
    }

    @Test
    fun writesExtensionCompatibleCsvAndEscapesNames() {
        val row = CardmarketExportRow(
            name = "Fire, Ice \"Test\"",
            setCode = "APC",
            setName = "Apocalypse",
            foil = true,
            quantity = 2,
            priceEur = 3.5,
            condition = "near_mint",
            language = "es",
        )

        val csv = CardmarketCsvExporter.toCsv(listOf(row))

        assertTrue(csv.startsWith(
            "Name,Set code,Foil,Signed,Rarity,Quantity,Purchase price,Condition,Language,Comment\r\n"
        ))
        assertTrue(csv.contains("\"Fire, Ice \"\"Test\"\"\",APC,foil,no,,2,3.50,near_mint,es,"))
        assertFalse(csv.startsWith("\uFEFF"))
    }

    @Test
    fun trialAlwaysContainsOnePhysicalCopy() {
        val row = CardmarketExportRow(
            "Card", "EOE", "Edge of Eternities", false, 7, 1.0, "near_mint", "en"
        )

        assertEquals(1, CardmarketCsvExporter.singleCopy(row).quantity)
        assertEquals(7, row.quantity)
    }

    private fun card(name: String, setCode: String) = CardInfo(name, "1.25 EUR", "", "", "1").apply {
        this.setCode = setCode
        setName = "Set $setCode"
        finish = "nonfoil"
        languageCode = "en"
        condition = "near_mint"
    }
}
