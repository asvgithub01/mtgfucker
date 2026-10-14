package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test

class ReviewCollectionScopeTest {
    private fun card(vararg groups: String)=CardInfo("A","","","","1").apply {
        groups.forEach { addGroup(it) }
    }

    @Test fun noSelectedGroupKeepsTheWholeLibrary() {
        for (group in listOf(null,""," ")) {
            assertTrue(ReviewCollectionScope.includes(card(),group))
            assertTrue(ReviewCollectionScope.includes(card("Triple 1"),group))
        }
    }

    @Test fun matchesExactMembershipIncludingCardsInMultipleGroups() {
        val selected=card("Triple 1","Caja FRA")
        assertTrue(ReviewCollectionScope.includes(selected,"Caja FRA"))
        assertFalse(ReviewCollectionScope.includes(selected,"Triple 10"))
        assertFalse(ReviewCollectionScope.includes(selected,"caja fra"))
        assertFalse(ReviewCollectionScope.includes(card(),"Caja FRA"))
    }

    @Test fun emptyOrRemovedGroupDoesNotFallBackToWholeLibrary() {
        val selected=card("Caja FRA")
        assertFalse(ReviewCollectionScope.includes(selected,"Otra caja"))
        selected.removeGroup("Caja FRA")
        assertFalse(ReviewCollectionScope.includes(selected,"Caja FRA"))
    }
}
