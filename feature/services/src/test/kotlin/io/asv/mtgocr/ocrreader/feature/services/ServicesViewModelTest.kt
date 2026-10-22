package io.asv.mtgocr.ocrreader.feature.services
import io.asv.mtgocr.ocrreader.domain.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ServicesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val snapshot = ServicesSnapshot(false, "EUR", true, emptyList())
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun readRequiresExplicitRefreshAndFailureClearsStaleState() = runTest(dispatcher) {
        var fail = false
        var reads = 0
        val model = ServicesViewModel(ServicesReader { reads++; if (fail) error("broken"); snapshot })
        advanceUntilIdle(); assertEquals(0, reads)
        model.refresh(); advanceUntilIdle(); assertEquals(snapshot, model.state.value.snapshot)
        fail = true; model.refresh(); assertNull(model.state.value.snapshot); advanceUntilIdle()
        assertTrue(model.state.value.failed)
        fail = false; model.refresh(); advanceUntilIdle()
        assertFalse(model.state.value.failed)
    }
    @Test fun lateResponseCannotOverwriteNewerRefresh() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val model = ServicesViewModel(ServicesReader {
            if (++reads == 1) withContext(NonCancellable) { gate.await(); snapshot.copy(currency = "USD") }
            else snapshot
        })
        model.refresh(); runCurrent(); model.refresh(); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals("EUR", model.state.value.snapshot!!.currency)
    }
}
