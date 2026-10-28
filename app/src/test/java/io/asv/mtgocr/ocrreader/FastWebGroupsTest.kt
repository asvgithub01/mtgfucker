package io.asv.mtgocr.ocrreader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
class FastWebGroupsTest {
    @Test fun fastWebPhysicalRowsMustNotBeFoldedIntoHistoricalCopies() {
        org.junit.Assert.assertTrue(FastWebGroups.ownsPhysicalCopy("FastWeb Triple 2026-10-25 20:13:26"))
        org.junit.Assert.assertTrue(FastWebGroups.ownsPhysicalCopy("FastWeb 3"))
        org.junit.Assert.assertFalse(FastWebGroups.ownsPhysicalCopy("Triple 2026-10-25 20:13:26"))
        org.junit.Assert.assertFalse(FastWebGroups.ownsPhysicalCopy("Cornelius 3"))
        org.junit.Assert.assertFalse(FastWebGroups.ownsPhysicalCopy("FastWeb pruebas"))
    }
    @Test fun selectedGroupOverridesAutomaticCreation() {
        assertEquals("Pruebas nuevas",FastWebGroups.destination(null,"Pruebas nuevas"))
        assertEquals("FastWeb 3",FastWebGroups.destination(null,"FastWeb 3"))
    }
    @Test fun recreationKeepsTheExistingSessionDestination() {
        assertEquals("Grupo guardado",FastWebGroups.destination("Grupo guardado","Otro grupo"))
    }
    @Test fun missingSelectionKeepsAutomaticFallback() {
        assertNull(FastWebGroups.destination(null,null))
        assertNull(FastWebGroups.destination("","  "))
    }
    @Test fun numberingRespectsSavedCounterAndExistingGroups() {
        assertEquals("FastWeb 1", FastWebGroups.next(emptyList(), 0))
        assertEquals("FastWeb 13", FastWebGroups.next(listOf("FastWeb 2", "FastWeb 12", "Other", "FastWeb nope"), 4))
        assertEquals("FastWeb 16", FastWebGroups.next(listOf("FastWeb 3"), 15))
    }
}
