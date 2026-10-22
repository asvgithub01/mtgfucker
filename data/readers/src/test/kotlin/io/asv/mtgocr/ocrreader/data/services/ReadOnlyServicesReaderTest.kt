package io.asv.mtgocr.ocrreader.data.services

import io.asv.mtgocr.ocrreader.domain.services.LibraryFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadOnlyServicesReaderTest {
    private class Source : ServicesReadSource {
        val calls = mutableListOf<String>()
        var premium = false
        var failBytes = false
        val refs = mutableListOf(ServiceLibraryReference("a", "Default", true, "one", "one.bak"),
            ServiceLibraryReference("b", "Extra", false, "two", "two.bak"))
        override fun premiumEnabled(): Boolean { calls += "premium"; return premium }
        override fun libraries(): List<ServiceLibraryReference> { calls += "libraries"; return refs }
        override fun bytes(fileName: String): Long? {
            calls += fileName
            check(!failBytes) { "unreadable" }
            return when (fileName) { "one" -> 0L; "one.bak" -> null; "two" -> Long.MAX_VALUE; else -> 12L }
        }
        override fun preferredCurrency(): String { calls += "currency"; return "USD" }
        override fun autoSyncPreference(): Boolean { calls += "autosync"; return false }
    }

    @Test fun retainsOrderAvailabilityAndIndependentPrimaryBackupSizes() = runTest {
        val source = Source()
        val snapshot = ReadOnlyServicesReader(source, StandardTestDispatcher(testScheduler)).read()
        assertFalse(snapshot.premium)
        assertEquals(listOf(LibraryFiles("a", "Default", true, 0, null),
            LibraryFiles("b", "Extra", false, Long.MAX_VALUE, 12)), snapshot.libraries)
        assertEquals("USD", snapshot.currency)
        assertFalse(snapshot.autoSyncPreference)
        assertEquals(listOf("premium", "libraries", "one", "one.bak", "two", "two.bak", "currency", "autosync"), source.calls)
    }

    @Test fun premiumDoesNotFilterOrMutateLibraryReferences() = runTest {
        val source = Source().apply { premium = true }
        val before = source.refs.toList()
        val snapshot = ReadOnlyServicesReader(source, StandardTestDispatcher(testScheduler)).read()
        assertTrue(snapshot.libraries.all { it.available })
        assertEquals(before, source.refs)
        source.refs.clear()
        assertEquals(2, snapshot.libraries.size)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun publishedFilesCannotBeMutatedThroughJavaList() = runTest {
        val snapshot = ReadOnlyServicesReader(Source(), StandardTestDispatcher(testScheduler)).read()
        (snapshot.libraries as MutableList).clear()
    }

    @Test fun readFailurePropagatesWithoutFabricatingZeroBytes() = runTest {
        val source = Source().apply { failBytes = true }
        try {
            ReadOnlyServicesReader(source, StandardTestDispatcher(testScheduler)).read()
            fail("read returned on failure")
        } catch (error: IllegalStateException) { assertEquals("unreadable", error.message) }
        assertEquals(listOf("premium", "libraries", "one"), source.calls)
    }

    @Test fun constructionDoesNotReadAndEachExplicitReadUsesInjectedDispatcher() = runTest {
        val source = Source()
        val reader = ReadOnlyServicesReader(source, StandardTestDispatcher(testScheduler))
        val first = async(start = CoroutineStart.UNDISPATCHED) { reader.read() }
        assertTrue(source.calls.isEmpty())
        advanceUntilIdle()
        assertFalse(first.await().premium)
        source.premium = true
        assertTrue(reader.read().premium)
    }
}
