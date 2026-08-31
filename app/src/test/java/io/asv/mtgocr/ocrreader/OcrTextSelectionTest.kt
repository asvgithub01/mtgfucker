package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextSelectionTest {
    @Test
    fun `keeps only first OCR line from a full card block`() {
        assertEquals(
            "Ice Storm",
            OcrTextSelection.firstPhrase("Ice Storm\nSorcery\nDestroy target land.")
        )
    }

    @Test
    fun `skips blank lines and preserves comma and dot for tolerant matching`() {
        assertEquals(
            "Who, What, When, Where, Why",
            OcrTextSelection.firstPhrase("\r\n  Who, What, When, Where, Why  \r\nInstant")
        )
    }

    @Test
    fun `keeps legacy OCR character cleanup on selected line`() {
        assertEquals("Black Lotus", OcrTextSelection.firstPhrase("(Black |Lotus\nArtifact"))
    }
}
