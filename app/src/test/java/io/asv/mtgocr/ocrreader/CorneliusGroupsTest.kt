package io.asv.mtgocr.ocrreader
import org.junit.Assert.assertEquals
import org.junit.Test
class CorneliusGroupsTest {
    @Test fun numberingRespectsSavedCounterAndExistingGroups() {
        assertEquals("Cornelius 1", CorneliusGroups.next(emptyList(), 0))
        assertEquals("Cornelius 13", CorneliusGroups.next(listOf("Cornelius 2", "Cornelius 12", "Other", "Cornelius nope"), 4))
        assertEquals("Cornelius 16", CorneliusGroups.next(listOf("Cornelius 3"), 15))
    }
}
