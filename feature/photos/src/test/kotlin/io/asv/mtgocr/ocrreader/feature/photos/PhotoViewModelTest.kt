package io.asv.mtgocr.ocrreader.feature.photos
import io.asv.mtgocr.ocrreader.domain.photos.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun refreshClearsStaleDataAndFailureCanRetry() = runTest(dispatcher) {
        var fail = false
        val model = PhotoViewModel(PhotoReader { id ->
            assertEquals("explicit", id)
            if (fail) error("read error")
            PhotoSnapshot(PhotoSection(), PhotoSection())
        }, "explicit")
        model.refresh(); advanceUntilIdle()
        assertFalse(model.state.value.snapshot!!.evidence.failed)
        fail = true; model.refresh(); assertNull(model.state.value.snapshot); advanceUntilIdle()
        assertTrue(model.state.value.snapshot!!.evidence.failed)
        fail = false; model.refresh(); advanceUntilIdle()
        assertFalse(model.state.value.snapshot!!.evidence.failed)
    }
    @Test fun ignoresLateUncancellableRead() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val model = PhotoViewModel(PhotoReader {
            if (++reads == 1) withContext(NonCancellable) { gate.await(); PhotoSnapshot(PhotoSection(failed = true), PhotoSection()) }
            else PhotoSnapshot(PhotoSection(), PhotoSection())
        }, "library")
        model.refresh(); runCurrent(); model.refresh(); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertFalse(model.state.value.snapshot!!.evidence.failed)
    }
}
