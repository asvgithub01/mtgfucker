package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class ConsecutiveArtworkEditionTest {
    @Test fun sameUndertakerArtKeepsMercadianDespiteTimeSpiralRankingFirst() {
        val state = ConsecutiveArtworkEdition()
        state.remember("undertaker-art", "MMQ-printing", "row")
        repeat(5) {
            state.observe("undertaker-art")
            assertEquals("MMQ-printing", state.retainedPrinting("undertaker-art", listOf("TSB-printing", "MMQ-printing")))
        }
    }

    @Test fun noSuccessfulSaveMeansNoRetainedEdition() {
        val state = ConsecutiveArtworkEdition()
        state.observe("art")
        assertNull(state.retainedPrinting("art", listOf("printing")))
    }

    @Test fun differentArtBreaksContinuityEvenIfOldArtReturns() {
        val state = ConsecutiveArtworkEdition()
        state.remember("art-a", "one", "row")
        state.observe("art-b")
        assertNull(state.retainedPrinting("art-b", listOf("one")))
        state.observe("art-a")
        assertNull(state.retainedPrinting("art-a", listOf("one")))
    }

    @Test fun neverReusesMissingArtworkOrPrintingOutsideCandidateCatalogue() {
        val state = ConsecutiveArtworkEdition()
        state.remember("art", "one", "row")
        assertNull(state.retainedPrinting("art", listOf("two")))
        assertNull(state.retainedPrinting(null, listOf("one")))
        state.remember(null, "one", "row")
        assertNull(state.retainedPrinting("art", listOf("one")))
    }

    @Test fun explicitSessionCorrectionOverridesRetainedEditionByStableId() {
        val state = ConsecutiveArtworkEdition()
        state.remember("art", "MMQ", "row")
        state.corrected("unrelated", "other")
        assertEquals("MMQ", state.retainedPrinting("art", listOf("MMQ", "TSB")))
        state.corrected("row", "TSB")
        assertEquals("TSB", state.retainedPrinting("art", listOf("MMQ", "TSB")))
    }

    @Test fun UndoLastCopyClearsRetentionButPartialUndoDoesNot() {
        val state = ConsecutiveArtworkEdition()
        state.remember("art", "one", "row")
        state.removed("unrelated", 0)
        state.removed("row", 2)
        assertEquals("one", state.retainedPrinting("art", listOf("one")))
        state.removed("row", 0)
        assertNull(state.retainedPrinting("art", listOf("one")))
    }
}
