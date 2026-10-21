package io.asv.mtgocr.ocrreader.data.catalog

import io.asv.mtgocr.ocrreader.domain.catalog.SetSummary
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallbackSetCatalogReaderTest {
    private val sets = listOf(SetSummary("SET", "Set", "2020", 1))
    @Test fun terminalApiDeliversOnlyFirstResult() = runTest {
        val reader = CallbackSetCatalogReader(SetCatalogSource { callback ->
            callback(sets, null); callback(emptyList(), null)
        })
        assertEquals(sets, reader.read())
    }
    @Test fun failureIsNotReportedAsAnEmptyCatalog() = runTest {
        val error = IllegalStateException("offline")
        try {
            CallbackSetCatalogReader(SetCatalogSource { it(emptyList(), error) }).read()
            fail("expected failure")
        } catch (actual: IllegalStateException) {
            // Coroutine stack-trace recovery may copy exceptions; identity is not the contract.
            assertEquals(error.message, actual.message)
        }
    }
    @Test fun lateCallbackAfterCancellationIsHarmless() = runTest {
        lateinit var callback: (List<SetSummary>, Throwable?) -> Unit
        val reader = CallbackSetCatalogReader(SetCatalogSource { callback = it })
        val job = launch { reader.read(); fail("cancelled read returned") }
        runCurrent()
        job.cancelAndJoin()
        callback(sets, null)
        callback(emptyList(), IllegalStateException("late"))
        assertTrue(job.isCancelled)
    }
}
