package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.MagicSetOption
import org.junit.Assert.assertEquals
import org.junit.Test

class MagicSetCatalogOrderTest {
    private val alpha = MagicSetOption("LEA", "Limited Edition Alpha", "1993-08-05", "core", 295)
    private val alliances = MagicSetOption("ALL", "Alliances", "1996-06-10", "expansion", 199)
    private val masters = MagicSetOption("2XM", "Double Masters", "2020-08-07", "masters", 332)

    @Test
    fun favoritesComeFirstAndTheRestStayChronological() {
        val result = MagicSetCatalogOrder.filterAndSort(
            listOf(masters, alliances, alpha),
            setOf("2XM"),
            ""
        )
        assertEquals(listOf("2XM", "LEA", "ALL"), result.map { it.code })
    }

    @Test
    fun searchIgnoresAccentsAndMatchesMetadata() {
        val accented = MagicSetOption("MÁG", "Edición Mágica", "2000-01-01", "especial", 10)
        assertEquals(
            listOf("MÁG"),
            MagicSetCatalogOrder.filterAndSort(listOf(alpha, accented), emptySet(), "magica")
                .map { it.code }
        )
    }
}
