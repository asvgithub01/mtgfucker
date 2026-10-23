package io.asv.mtgocr.ocrreader.data.legacy

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObservedLibraryChangesTest {
    private class Source : LibraryDirectoryObserver {
        val listeners = mutableListOf<(String?) -> Unit>()
        var stops = 0
        override fun watch(onEvent: (String?) -> Unit): () -> Unit {
            listeners += onEvent
            return { stops++; listeners.remove(onEvent) }
        }
        fun emit(path: String?) = listeners.toList().forEach { it(path) }
    }

    @Test fun coldRegistrationEmitsInitialInvalidationAfterOriginalDebounce() = runTest {
        val source = Source()
        val flow = observedLibraryChanges(source, setOf("library"))
        assertTrue(source.listeners.isEmpty())
        val received = mutableListOf<Unit>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(received) }
        runCurrent()
        assertEquals(1, source.listeners.size)
        advanceTimeBy(149); runCurrent()
        assertTrue(received.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(1, received.size)
        job.cancelAndJoin()
        assertEquals(1, source.stops)
    }

    @Test fun ignoresNullAndUnrelatedPathsButAcceptsPrimaryAndBackup() = runTest {
        val source = Source()
        val received = mutableListOf<Unit>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observedLibraryChanges(source, setOf("library", "library.bak")).toList(received)
        }
        runCurrent(); advanceTimeBy(151); runCurrent()
        source.emit(null); source.emit("other"); source.emit("library.tmp")
        runCurrent(); advanceTimeBy(151); runCurrent()
        assertEquals(1, received.size)
        for (name in listOf("library", "library.bak")) {
            source.emit(name); runCurrent(); advanceTimeBy(151); runCurrent()
        }
        assertEquals(3, received.size)
        job.cancelAndJoin()
    }

    @Test fun burstIsConflatedAndLaterPublicationRestartsDebounce() = runTest {
        val source = Source()
        val received = mutableListOf<Unit>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observedLibraryChanges(source, setOf("library")).toList(received)
        }
        runCurrent(); advanceTimeBy(151); runCurrent()
        repeat(1000) { source.emit("library") }
        runCurrent(); advanceTimeBy(100); runCurrent()
        source.emit("library"); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertEquals(1, received.size)
        advanceTimeBy(51); runCurrent()
        assertEquals(2, received.size)
        job.cancelAndJoin()
    }

    @Test fun eachCollectorOwnsItsRegistrationAndCancellation() = runTest {
        val source = Source()
        val flow = observedLibraryChanges(source, setOf("library"))
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect() }
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect() }
        runCurrent()
        assertEquals(2, source.listeners.size)
        first.cancelAndJoin()
        assertEquals(1, source.listeners.size)
        assertEquals(1, source.stops)
        second.cancelAndJoin()
        assertTrue(source.listeners.isEmpty())
        assertEquals(2, source.stops)
    }

    @Test fun cancellationBeforeDebounceDropsPendingAndClosesObserver() = runTest {
        val source = Source()
        val received = mutableListOf<Unit>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observedLibraryChanges(source, setOf("library")).toList(received)
        }
        runCurrent()
        val lateCallback = source.listeners.single()
        job.cancelAndJoin()
        lateCallback("library") // callback racing with native observer shutdown is harmless
        advanceTimeBy(151); runCurrent()
        assertTrue(received.isEmpty())
        assertEquals(1, source.stops)
    }

    @Test fun synchronousEventDuringRegistrationIsNotLost() = runTest {
        var stopped = false
        val source = LibraryDirectoryObserver { event ->
            event("library")
            ({ stopped = true })
        }
        assertEquals(Unit, observedLibraryChanges(source, setOf("library")).first())
        runCurrent()
        assertTrue(stopped)
    }

    @Test fun registrationErrorPropagatesInsteadOfReportingAChange() = runTest {
        val source = LibraryDirectoryObserver { throw IllegalStateException("cannot observe") }
        try { observedLibraryChanges(source, setOf("library")).first(); fail("expected failure") }
        catch (error: IllegalStateException) { assertEquals("cannot observe", error.message) }
    }
}
