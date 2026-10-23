package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.collection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadOnlyCollectionReaderTest {
    private fun row(id: String) = CollectionRow(id, "Localized", "printing", "foil", "pt", "good",
        3, "image", listOf("group"), listOf("deck"), listOf("sideboard"), 42, "Set", "SET", "7", "Text", "2 EUR", 2)

    @Test fun explicitLibraryAndRowOrderArePreserved() = runTest {
        val expected = listOf(row("b"), row("a"))
        var requested = ""
        val reader = ReadOnlyCollectionReader(CollectionReadSource {
            requested = it
            CollectionRowsLoad.Loaded(expected.asSequence().map { r -> CollectionRowRead.Ready(r.id) { r } })
        }, StandardTestDispatcher(testScheduler))
        val result = reader.read("extra") as CollectionReadResult.Ready
        assertEquals("extra", requested)
        assertEquals(CollectionSnapshot("extra", expected), result.snapshot)
    }

    @Test fun missingEmptyAndAllSourceFailuresStayDistinct() = runTest {
        suspend fun read(value: CollectionRowsLoad) = ReadOnlyCollectionReader(CollectionReadSource { value },
            StandardTestDispatcher(testScheduler)).read("extra")
        assertEquals(CollectionReadResult.Missing, read(CollectionRowsLoad.Missing))
        assertEquals(CollectionReadResult.Ready(CollectionSnapshot("extra", emptyList())),
            read(CollectionRowsLoad.Loaded(emptySequence())))
        CollectionReadFailure.values().forEach {
            assertEquals(CollectionReadResult.Failed(it), read(CollectionRowsLoad.Failed(it)))
        }
    }

    @Test fun duplicateStopsBeforeMappingDuplicateOrReadingFollowingEntries() = runTest {
        val calls = mutableListOf<String>()
        val entries = sequence {
            calls += "first"
            yield(CollectionRowRead.Ready("a") { calls += "map-first"; row("a") })
            calls += "duplicate"
            yield(CollectionRowRead.Ready("a") { error("must not map duplicate") })
            error("must not inspect later entries")
        }
        val result = ReadOnlyCollectionReader(CollectionReadSource { CollectionRowsLoad.Loaded(entries) },
            StandardTestDispatcher(testScheduler)).read("extra")
        assertEquals(CollectionReadResult.Failed(CollectionReadFailure.DUPLICATE_IDENTITY), result)
        assertEquals(listOf("first", "map-first", "duplicate"), calls)
    }

    @Test fun invalidEntryDiscardsPartialResultAndStopsReading() = runTest {
        for (reason in listOf(CollectionReadFailure.UNREADABLE, CollectionReadFailure.MISSING_IDENTITY)) {
            val entries = sequence {
                yield(CollectionRowRead.Ready("a") { row("a") })
                yield(CollectionRowRead.Failed(reason))
                error("must stop after invalid entry")
            }
            assertEquals(CollectionReadResult.Failed(reason),
                ReadOnlyCollectionReader(CollectionReadSource { CollectionRowsLoad.Loaded(entries) },
                    StandardTestDispatcher(testScheduler)).read("extra"))
        }
    }

    @Test fun readsAreLazyDispatchedAndNotCached() = runTest {
        val calls = mutableListOf<String>()
        val reader = ReadOnlyCollectionReader(CollectionReadSource {
            calls += it
            CollectionRowsLoad.Loaded(sequence {
                calls += "iterate"
                yield(CollectionRowRead.Ready(it) { calls += "map"; row(it) })
            })
        }, StandardTestDispatcher(testScheduler))
        val pending = async(start = CoroutineStart.UNDISPATCHED) { reader.read("a") }
        assertTrue(calls.isEmpty())
        advanceUntilIdle()
        assertTrue(pending.await() is CollectionReadResult.Ready)
        reader.read("b")
        assertEquals(listOf("a", "iterate", "map", "b", "iterate", "map"), calls)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun publishedRowsCannotBeMutated() = runTest {
        val reader = ReadOnlyCollectionReader(CollectionReadSource {
            CollectionRowsLoad.Loaded(sequenceOf(CollectionRowRead.Ready("a") { row("a") }))
        }, StandardTestDispatcher(testScheduler))
        val result = reader.read("extra") as CollectionReadResult.Ready
        (result.snapshot.rows as MutableList).clear()
    }

    @Test fun exceptionsAndCancellationNeverBecomeEmptyData() = runTest {
        for (error in listOf(IllegalStateException("broken"), CancellationException("cancelled"))) {
            for (duringMapping in listOf(false, true)) {
                val reader = ReadOnlyCollectionReader(CollectionReadSource {
                    if (!duringMapping) throw error
                    CollectionRowsLoad.Loaded(sequenceOf(CollectionRowRead.Ready("a") { throw error }))
                }, StandardTestDispatcher(testScheduler))
                try { reader.read("extra"); fail("expected propagation") }
                catch (actual: Exception) {
                    assertEquals(error.javaClass, actual.javaClass)
                    assertEquals(error.message, actual.message)
                }
            }
        }
    }
}
