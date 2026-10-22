package io.asv.mtgocr.ocrreader.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.asv.mtgocr.ocrreader.domain.catalog.SetCatalogReader
import io.asv.mtgocr.ocrreader.domain.catalog.SetSearchMatcher
import io.asv.mtgocr.ocrreader.domain.catalog.SetSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Module tests use ports only; the real legacy search rules are still tested in app. */
@OptIn(ExperimentalCoroutinesApi::class)
class SetCatalogContractTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val sets = listOf(SetSummary("OLD", "Older", "2020", 1),
        SetSummary("NEW", "Newer", "2026", 2))

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun delegatesFilteringAndRestoresSelectionByStableCode() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        val matcher = SetSearchMatcher { set, query -> queries += query; set.code == query }
        val saved = SavedStateHandle(mapOf("catalogQuery" to "NEW", "catalogSelected" to "OLD"))
        val vm = SetCatalogViewModel(SetCatalogReader { sets }, matcher, saved, dispatcher)
        store.put("catalog", vm)
        advanceUntilIdle()
        assertEquals(listOf("NEW"), vm.state.value.sets.map { it.code })
        assertEquals("OLD", vm.state.value.selected?.code)
        assertTrue(queries.contains("NEW"))
        vm.query("OLD"); advanceUntilIdle()
        assertEquals("OLD", saved.get<String>("catalogQuery"))
        assertEquals(listOf("OLD"), vm.state.value.sets.map { it.code })
    }

    @Test fun duplicateIdentitiesFailClosedAndRetryRecovers() = runTest(dispatcher) {
        var broken = true
        val vm = SetCatalogViewModel(SetCatalogReader { if (broken) listOf(sets[0], sets[0]) else sets },
            SetSearchMatcher { _, _ -> true }, SavedStateHandle(), dispatcher)
        store.put("catalog", vm)
        advanceUntilIdle()
        assertTrue(vm.state.value.failed)
        assertTrue(vm.state.value.sets.isEmpty())
        broken = false
        vm.reload(); advanceUntilIdle()
        assertFalse(vm.state.value.failed)
        assertEquals(listOf("NEW", "OLD"), vm.state.value.sets.map { it.code })
    }
}
