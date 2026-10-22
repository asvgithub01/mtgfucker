package io.asv.mtgocr.ocrreader.feature.launcher

import androidx.lifecycle.SavedStateHandle
import io.asv.mtgocr.ocrreader.domain.launcher.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = LauncherData(listOf(LauncherLibrary("a", "A"), LauncherLibrary("b", "B")), "a", null)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class Fake(var data: LauncherData) : LauncherRepository {
        var reads = 0
        var writes = 0
        var failure = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): LauncherData {
            reads++
            gate?.await()
            if (failure) error("offline")
            return data
        }
        override suspend fun select(libraryId: String): LauncherData {
            writes++
            data = data.copy(selectedId = libraryId)
            return load()
        }
    }

    @Test fun loadDoesNotSelectOrWrite() = runTest(dispatcher) {
        val fake = Fake(data)
        val vm = LauncherViewModel(fake, SavedStateHandle())
        assertNull(vm.prepareOpen())
        vm.refresh(); advanceUntilIdle()
        assertEquals(data, vm.state.value.data)
        assertEquals(0, fake.writes)
        assertTrue(vm.state.value.canOpen)
    }

    @Test fun pricePromptOnceIncludingRestoredProcessState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = LauncherViewModel(Fake(data), saved)
        vm.refresh(); advanceUntilIdle()
        assertEquals(true, vm.prepareOpen())
        assertNull(vm.prepareOpen()) // double tap
        vm.navigationSucceeded()
        val restored = LauncherViewModel(Fake(data), SavedStateHandle(mapOf("pricePromptSent" to saved.get<Boolean>("pricePromptSent"))))
        restored.refresh(); advanceUntilIdle()
        assertEquals(false, restored.prepareOpen())
    }

    @Test fun failedNavigationDoesNotConsumePrompt() = runTest(dispatcher) {
        val vm = LauncherViewModel(Fake(data), SavedStateHandle())
        vm.refresh(); advanceUntilIdle()
        assertEquals(true, vm.prepareOpen())
        vm.navigationFailed()
        vm.refresh(); advanceUntilIdle()
        assertEquals(true, vm.prepareOpen())
    }

    @Test fun unreadableCollectionBlocksNavigation() = runTest(dispatcher) {
        val vm = LauncherViewModel(Fake(data.copy(readable = false)), SavedStateHandle())
        vm.refresh(); advanceUntilIdle()
        assertFalse(vm.state.value.canOpen)
        assertNull(vm.prepareOpen())
    }

    @Test fun errorRetainsDataButBlocksOpenAndCanRetry() = runTest(dispatcher) {
        val fake = Fake(data)
        val vm = LauncherViewModel(fake, SavedStateHandle())
        vm.refresh(); advanceUntilIdle()
        fake.failure = true
        vm.refresh(); advanceUntilIdle()
        assertTrue(vm.state.value.failed)
        assertEquals(data, vm.state.value.data)
        assertNull(vm.prepareOpen())
        fake.failure = false
        vm.refresh(); advanceUntilIdle()
        assertTrue(vm.state.value.canOpen)
    }

    @Test fun selectionIsExplicitAndSingleFlight() = runTest(dispatcher) {
        val fake = Fake(data)
        val vm = LauncherViewModel(fake, SavedStateHandle())
        vm.refresh(); advanceUntilIdle()
        fake.gate = CompletableDeferred()
        vm.select("b")
        vm.select("a")
        vm.refresh()
        runCurrent()
        assertEquals(1, fake.writes)
        assertNull(vm.prepareOpen())
        fake.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals("b", vm.state.value.data!!.selectedId)
    }

    @Test fun unknownSelectionDoesNotChangePreference() = runTest(dispatcher) {
        val fake = Fake(data)
        val vm = LauncherViewModel(fake, SavedStateHandle())
        vm.refresh(); advanceUntilIdle(); vm.select("unknown"); advanceUntilIdle()
        assertEquals(0, fake.writes)
    }

    @Test fun returningReloadsPremiumChangesAndUnlocksNavigation() = runTest(dispatcher) {
        val fake = Fake(data)
        val vm = LauncherViewModel(fake, SavedStateHandle())
        vm.refresh(); advanceUntilIdle(); vm.prepareOpen(); vm.navigationSucceeded()
        fake.data = data.copy(libraries = listOf(data.libraries.first()))
        vm.refresh(); advanceUntilIdle()
        assertTrue(vm.state.value.canOpen)
        assertEquals(1, vm.state.value.data!!.libraries.size)
        assertEquals(false, vm.prepareOpen())
    }
}
