package io.asv.mtgocr.ocrreader.data.library

import io.asv.mtgocr.ocrreader.domain.collection.*
import io.asv.mtgocr.ocrreader.domain.library.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadOnlyLibraryShellRepositoryTest {
    private class Source : LibraryShellReadSource {
        val calls = mutableListOf<String>()
        var active = "a"
        var collectionResult: CollectionReadResult = CollectionReadResult.Missing
        var deckResult: DeckReadResult = DeckReadResult.Ready(emptyList())
        var duringCollection: suspend () -> Unit = {}
        var duringDecks: () -> Unit = {}
        val events: Flow<Unit> = flowOf(Unit)
        override fun activeLibraryId(): String { calls += "active"; return active }
        override suspend fun collection(libraryId: String): CollectionReadResult {
            calls += "collection:$libraryId"; duringCollection(); return collectionResult
        }
        override fun decks(libraryId: String): DeckReadResult {
            calls += "decks:$libraryId"; duringDecks(); return deckResult
        }
        override fun changes(libraryId: String): Flow<Unit> { calls += "changes:$libraryId"; return events }
    }

    @Test fun mismatchedActiveLibraryStopsBeforeAnyFileRead() = runTest {
        val source = Source()
        val repo = ReadOnlyLibraryShellRepository(source, StandardTestDispatcher(testScheduler))
        assertEquals(ShellReadResult.Failed(ShellReadFailure.LIBRARY_CHANGED), repo.read("b"))
        assertEquals(listOf("active"), source.calls)
    }

    @Test fun emptyMissingCollectionKeepsRequestedIdAndReadOrder() = runTest {
        val source = Source()
        val repo = ReadOnlyLibraryShellRepository(source, StandardTestDispatcher(testScheduler))
        val result = repo.read("a") as ShellReadResult.Ready
        assertEquals(CollectionSnapshot("a", emptyList()), result.snapshot.collection)
        assertTrue(result.snapshot.decks.isEmpty())
        assertEquals(listOf("active", "collection:a", "decks:a", "active"), source.calls)
    }

    @Test fun failedCollectionDoesNotReadDecksOrReturnAnEmptySuccess() = runTest {
        val source = Source().apply { collectionResult = CollectionReadResult.Failed(CollectionReadFailure.UNREADABLE) }
        val repo = ReadOnlyLibraryShellRepository(source, StandardTestDispatcher(testScheduler))
        assertEquals(ShellReadResult.Failed(ShellReadFailure.COLLECTION_UNREADABLE), repo.read("a"))
        assertEquals(listOf("active", "collection:a"), source.calls)
    }

    @Test fun corruptDecksRemainDistinctFromAnAbsentCatalog() = runTest {
        val source = Source().apply { deckResult = DeckReadResult.Unreadable }
        val repo = ReadOnlyLibraryShellRepository(source, StandardTestDispatcher(testScheduler))
        assertEquals(ShellReadResult.Failed(ShellReadFailure.DECKS_UNREADABLE), repo.read("a"))
        assertEquals(listOf("active", "collection:a", "decks:a"), source.calls)
    }

    @Test fun librarySwitchDuringReadRejectsSnapshotAndKeepsExplicitDeckId() = runTest {
        val source = Source().apply { duringCollection = { active = "b" } }
        val repo = ReadOnlyLibraryShellRepository(source, StandardTestDispatcher(testScheduler))
        assertEquals(ShellReadResult.Failed(ShellReadFailure.LIBRARY_CHANGED), repo.read("a"))
        assertEquals(listOf("active", "collection:a", "decks:a", "active"), source.calls)
        source.active = "a"
        source.duringCollection = {}
        source.duringDecks = { source.active = "b" }
        assertEquals(ShellReadResult.Failed(ShellReadFailure.LIBRARY_CHANGED), repo.read("a"))
    }

    @Test fun deckDefinitionsAreDetachedAndUnmodifiable() = runTest {
        val decks = mutableListOf(DeckSummaryDefinition("Empty deck", "free", 42))
        val source = Source().apply { deckResult = DeckReadResult.Ready(decks) }
        val repo = ReadOnlyLibraryShellRepository(source, StandardTestDispatcher(testScheduler))
        val result = repo.read("a") as ShellReadResult.Ready
        decks.clear()
        assertEquals("Empty deck", result.snapshot.decks.single().name)
        try { (result.snapshot.decks as MutableList).clear(); fail("mutable snapshot") }
        catch (_: UnsupportedOperationException) { }
    }

    @Test fun changesDelegateWithoutTriggeringARead() = runTest {
        val source = Source()
        val repo = ReadOnlyLibraryShellRepository(source, StandardTestDispatcher(testScheduler))
        assertSame(source.events, repo.changes("explicit"))
        assertEquals(listOf("changes:explicit"), source.calls)
    }

    @Test fun usesInjectedDispatcherAndPropagatesCancellation() = runTest {
        val source = Source().apply { duringCollection = { throw CancellationException("cancel read") } }
        val repo = ReadOnlyLibraryShellRepository(source, StandardTestDispatcher(testScheduler))
        val job = async(start = CoroutineStart.UNDISPATCHED) { repo.read("a") }
        assertTrue(source.calls.isEmpty())
        advanceUntilIdle()
        try { job.await(); fail("cancelled read returned") } catch (_: CancellationException) { }
        assertEquals(listOf("active", "collection:a"), source.calls)
    }
}
