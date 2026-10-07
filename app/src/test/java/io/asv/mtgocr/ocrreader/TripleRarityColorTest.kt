package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class TripleRarityColorTest {
    @Test fun raritiesAreDistinctAndOpaque() {
        val colors=listOf("common","uncommon","rare","mythic").map { TripleRarityColor.color(it) }
        assertEquals(4,colors.distinct().size)
        colors.forEach { assertEquals(255,it ushr 24) }
    }
    @Test fun normalizeAndFallback() {
        assertEquals(TripleRarityColor.color("rare"),TripleRarityColor.color(" RARE "))
        assertEquals(TripleRarityColor.color("mythic"),TripleRarityColor.color("mythic rare"))
        assertEquals(TripleRarityColor.color(null),TripleRarityColor.color(""))
        assertNotEquals(TripleRarityColor.color("rare"),TripleRarityColor.color("unknown"))
    }
}
