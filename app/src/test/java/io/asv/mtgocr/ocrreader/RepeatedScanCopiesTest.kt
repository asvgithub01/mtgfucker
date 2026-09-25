package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class RepeatedScanCopiesTest {
    @Test fun correctedBatchIncludesFirstCopyWithoutCountingOldInventory() {
        val state = RepeatedScanCopies()
        state.added(RepeatedScanCopies.Key("MMQ", "nonfoil", "pt"), "old-row", 1)
        val corrected = RepeatedScanCopies.Key("TSB", "nonfoil", "pt")
        state.replaceBatch(corrected, "new-row", 4)
        assertEquals(4, state.saved)
        assertEquals("new-row", state.collectionItemId)
        assertTrue(state.matches(corrected))
        assertEquals(1, state.additional("5"))
    }
    @Test fun editionCorrectionInvalidatesOnlyTheCorrectedBatch() {
        val state = RepeatedScanCopies()
        val original = RepeatedScanCopies.Key("old", "nonfoil", "pt")
        state.added(original, "row", 3)
        state.editionChanged("another")
        assertTrue(state.matches(original))
        state.editionChanged("row")
        assertFalse(state.matches(original))
        assertEquals(0, state.saved)
    }
    private val key = RepeatedScanCopies.Key("uuid", "nonfoil", "en")
    @Test fun secondDetectionDoesNotAddAndTotalFourOnlyAddsThree() {
        val state = RepeatedScanCopies()
        assertFalse(state.matches(key))
        state.added(key, "row", 1)
        repeat(10) { assertTrue(state.matches(key)); assertEquals(1, state.saved) }
        assertEquals(3, state.additional("4"))
        state.added(key, "row", 3)
        assertEquals(4, state.saved)
        assertEquals(0, state.additional("4"))
        assertNull(state.additional("3")) // no implicit deletion of already saved copies
    }
    @Test fun invalidInputCancelAndUndoDoNotInventCopies() {
        val state = RepeatedScanCopies()
        state.added(key, "row", 1)
        listOf("", "0", "-1", "100", "9999999999999", "1.5").forEach { assertNull(state.additional(it)) }
        assertEquals(0, state.additional("1"))
        state.removed("other")
        assertEquals(1, state.saved)
        state.removed("row")
        assertFalse(state.matches(key))
    }
    @Test fun differentPrintingsFinishesOrLanguagesAreNotRepeated() {
        val state = RepeatedScanCopies()
        state.added(key, "row", 4)
        assertFalse(state.matches(key.copy(printingUuid = "other")))
        assertFalse(state.matches(key.copy(finish = "foil")))
        assertFalse(state.matches(key.copy(language = "es")))
        val other = key.copy(printingUuid = "other")
        state.added(other, "otherRow", 1)
        assertEquals(1, state.saved)
        assertFalse(state.matches(key))
        state.added(key, "row", 1)
        assertEquals(1, state.saved) // prior library copies do not enter the new batch
    }
}
