package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardEditionOption
import org.junit.Assert.*
import org.junit.Test

class EditionGridPolicyTest {
    private fun option(id: String, set: String = "SPG", finish: String = "nonfoil") = CardEditionOption(
        id, "Card", "Card", set, "Set $set", "150", "2026-01-01", "rare", finish, finish == "foil",
        null, "", "", null, null, null, null)

    @Test fun oneGridTilePerSetRegardlessOfPrintingsAndFinishes() {
        val options = listOf(option("one"), option("one", finish = "foil"), option("two"), option("three", "STX"))
        assertEquals(listOf("SPG", "STX"), EditionGridPolicy.sets(options).map { it.code })
    }

    @Test fun preservesFinishAndNeverPicksArbitraryArtwork() {
        val options = listOf(option("one"), option("two"), option("one", finish = "foil"))
        assertEquals(listOf("one", "two"), EditionGridPolicy.choices(options, "spg", "normal").map { it.printingUuid })
        assertEquals("foil", EditionGridPolicy.choices(options, "SPG", "foil").single().finish)
        assertTrue(EditionGridPolicy.choices(options, "STX", "nonfoil").isEmpty())
    }

    @Test fun unavailableFinishDoesNotHideEditionAndDuplicateRowsCollapse() {
        val foil = option("one", finish = "foil")
        assertEquals(listOf(foil), EditionGridPolicy.choices(listOf(foil, foil), "SPG", "nonfoil"))
    }
}
