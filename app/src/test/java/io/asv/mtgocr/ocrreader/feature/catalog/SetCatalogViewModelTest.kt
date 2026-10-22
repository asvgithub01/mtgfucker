package io.asv.mtgocr.ocrreader.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.asv.mtgocr.ocrreader.data.catalog.LegacySetSearchMatcher
import io.asv.mtgocr.ocrreader.domain.catalog.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class SetCatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val sets = listOf(SetSummary("AAA", "Edición antigua", "2020-01-01", 5),
        SetSummary("BBB", "Nueva edición", "2025-01-01", 10))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun vm(reader: SetCatalogReader, saved: SavedStateHandle = SavedStateHandle()) =
        SetCatalogViewModel(reader, LegacySetSearchMatcher, saved, dispatcher).also {
            stores += ViewModelStore().apply { put("catalog", it) }
        }
    @Test fun readsOnceAndUsesExistingUppercaseAndAccentRules() = runTest(dispatcher) {
        var reads = 0
        val vm = vm(SetCatalogReader { reads++; sets })
        advanceUntilIdle()
        assertEquals(listOf("BBB", "AAA"), vm.state.value.sets.map { it.code })
        vm.query("edicion"); advanceUntilIdle(); assertEquals(2, vm.state.value.sets.size)
        vm.query("AAA"); advanceUntilIdle(); assertEquals("AAA", vm.state.value.sets.single().code)
        vm.query("EDICION"); advanceUntilIdle(); assertTrue(vm.state.value.sets.isEmpty())
        assertEquals(1, reads)
    }
    @Test fun restoredSelectionIsByCodeNotPositionOrVisibleFilter() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("catalogQuery" to "BBB", "catalogSelected" to "AAA"))
        val vm = vm(SetCatalogReader { sets }, saved); advanceUntilIdle()
        assertEquals("AAA", vm.state.value.selected!!.code)
        assertEquals("BBB", vm.state.value.sets.single().code)
        vm.select("missing"); advanceUntilIdle(); assertEquals("AAA", vm.state.value.selected!!.code)
        vm.dismiss(); advanceUntilIdle(); assertNull(vm.state.value.selected)
    }
    @Test fun lateResponseCannotReplaceNewerReload() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val vm = vm(SetCatalogReader {
            if (++reads == 1) withContext(NonCancellable) { gate.await(); listOf(sets[0]) }
            else listOf(sets[1])
        })
        runCurrent(); vm.reload(); runCurrent(); gate.complete(Unit); advanceUntilIdle()
        assertEquals("BBB", vm.state.value.sets.single().code)
    }
    @Test fun errorClearsStaleDataAndRetryRecoversWithoutLosingQuery() = runTest(dispatcher) {
        var fail = false
        val vm = vm(SetCatalogReader { if (fail) error("offline") else sets })
        advanceUntilIdle(); vm.query("AAA"); fail = true; vm.reload(); advanceUntilIdle()
        assertTrue(vm.state.value.failed); assertTrue(vm.state.value.sets.isEmpty())
        assertNull(vm.state.value.selected)
        fail = false; vm.reload(); advanceUntilIdle()
        assertFalse(vm.state.value.failed); assertEquals("AAA", vm.state.value.sets.single().code)
    }
    @Test fun duplicateCodesAreRejectedAndEmptyCatalogIsNotError() = runTest(dispatcher) {
        var values = listOf(sets[0], sets[0])
        val vm = vm(SetCatalogReader { values }); advanceUntilIdle()
        assertTrue(vm.state.value.failed)
        values = emptyList(); vm.reload(); advanceUntilIdle()
        assertFalse(vm.state.value.failed); assertTrue(vm.state.value.sets.isEmpty())
    }
}
