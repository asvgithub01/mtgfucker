package io.asv.mtgocr.ocrreader.domain.library

import io.asv.mtgocr.ocrreader.domain.collection.*
import org.junit.Assert.*
import org.junit.Test

class LibraryPresentationTest {
    private fun row(id: String, quantity: Int = 1, addedAt: Long = 0) = CollectionRow(
        id, "Ángel $id", "printing-$id", "foil", "es", "good", quantity, null,
        listOf("Grupo"), listOf("Mazo"), emptyList(), addedAt, "Edición", "SET"
    )
    private fun snapshot(vararg rows: CollectionRow) =
        LibraryShellSnapshot(CollectionSnapshot("library", rows.toList()),
            listOf(DeckSummaryDefinition("Vacío", "free", 0)))

    @Test fun newestIsStableWithoutMutatingStoredOrder() {
        val original = snapshot(row("a", addedAt = 1), row("b", addedAt = 2), row("c", addedAt = 2))
        assertEquals(listOf("b", "c", "a"),
            LibraryPresentation.rows(original, LibrarySection.CARDS, null, "", LibrarySort.NEWEST).map { it.id })
        assertEquals(listOf("a", "b", "c"), original.collection.rows.map { it.id })
    }
    @Test fun searchMatchesLocalizedNameSetAndCodeIgnoringAccents() {
        val snapshot = snapshot(row("a"))
        for (query in listOf("angel", "EDICION", "set")) {
            assertEquals(1, LibraryPresentation.rows(snapshot, LibrarySection.CARDS, null, query, LibrarySort.NAME).size)
        }
    }
    @Test fun groupsAndDecksWithSameNameStayDistinct() {
        val snapshot = snapshot(row("a").copy(groups = listOf("Same"), decks = emptyList()),
            row("b").copy(groups = emptyList(), decks = listOf("Same")))
        assertEquals("a", LibraryPresentation.rows(snapshot, LibrarySection.GROUPS, "Same", "", LibrarySort.NAME).single().id)
        assertEquals("b", LibraryPresentation.rows(snapshot, LibrarySection.DECKS, "Same", "", LibrarySort.NAME).single().id)
    }
    @Test fun copyCountsDoNotCountDuplicateMembershipOrLoseSideboard() {
        val snapshot = snapshot(row("a", 3).copy(decks = listOf("Mazo", "Mazo")),
            row("b", 2).copy(sideboardDecks = listOf("Mazo")))
        val deck = LibraryPresentation.memberships(snapshot, LibrarySection.DECKS).first { it.name == "Mazo" }
        assertEquals(5L, deck.copies)
        assertEquals(2L, deck.sideboard)
    }
    @Test fun emptyDeckDefinitionsAreNotLost() {
        val deck = LibraryPresentation.memberships(snapshot(), LibrarySection.DECKS).single()
        assertEquals("Vacío", deck.name)
        assertEquals(0L, deck.copies)
    }
    @Test fun totalsUseLongAndIdsAreNotMergedByName() {
        val snapshot = snapshot(row("a", Int.MAX_VALUE).copy(name = "Same"), row("b", Int.MAX_VALUE).copy(name = "Same"))
        assertEquals(4294967294L, LibraryPresentation.copies(snapshot.collection.rows))
        assertEquals(2, LibraryPresentation.rows(snapshot, LibrarySection.CARDS, null, "same", LibrarySort.NAME).size)
    }
}
