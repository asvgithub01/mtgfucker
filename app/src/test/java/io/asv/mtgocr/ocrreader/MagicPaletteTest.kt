package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class MagicPaletteTest {
    @Test fun `accepts every available Magic palette`() {
        listOf("green", "red", "blue", "black", "white", "metal").forEach {
            assertEquals(it, MagicPalette.normalizeId(it))
        }
    }

    @Test fun `normalizes case and falls back safely`() {
        assertEquals(MagicPalette.BLUE, MagicPalette.normalizeId(" BLUE "))
        assertEquals(MagicPalette.GREEN, MagicPalette.normalizeId("unknown"))
        assertEquals(MagicPalette.GREEN, MagicPalette.normalizeId(null))
    }
}
