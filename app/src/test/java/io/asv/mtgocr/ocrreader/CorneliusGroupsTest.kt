package io.asv.mtgocr.ocrreader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
class CorneliusGroupsTest {
    @Test fun selectedGroupOverridesAutomaticCreation() {
        assertEquals("Pruebas nuevas",CorneliusGroups.destination(null,"Pruebas nuevas"))
        assertEquals("Cornelius 3",CorneliusGroups.destination(null,"Cornelius 3"))
    }
    @Test fun recreationKeepsTheExistingSessionDestination() {
        assertEquals("Grupo guardado",CorneliusGroups.destination("Grupo guardado","Otro grupo"))
    }
    @Test fun missingSelectionKeepsAutomaticFallback() {
        assertNull(CorneliusGroups.destination(null,null))
        assertNull(CorneliusGroups.destination("","  "))
    }
    @Test fun numberingRespectsSavedCounterAndExistingGroups() {
        assertEquals("Cornelius 1", CorneliusGroups.next(emptyList(), 0))
        assertEquals("Cornelius 13", CorneliusGroups.next(listOf("Cornelius 2", "Cornelius 12", "Other", "Cornelius nope"), 4))
        assertEquals("Cornelius 16", CorneliusGroups.next(listOf("Cornelius 3"), 15))
    }
}
