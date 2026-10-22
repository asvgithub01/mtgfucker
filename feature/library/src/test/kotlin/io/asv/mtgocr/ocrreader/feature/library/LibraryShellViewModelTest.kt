package io.asv.mtgocr.ocrreader.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.asv.mtgocr.ocrreader.domain.collection.*
import io.asv.mtgocr.ocrreader.domain.library.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryShellViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun ready(id: String = "lib", rowId: String = "row") = ShellReadResult.Ready(
        LibraryShellSnapshot(CollectionSnapshot(id, listOf(CollectionRow(
            rowId, "Test", null, null, "", "good", 3, null, listOf("Grupo"), emptyList(), emptyList()
        ))), emptyList()))
    private fun vm(repo: LibraryShellRepository, saved: SavedStateHandle = SavedStateHandle()) =
        LibraryShellViewModel("lib", repo, saved, dispatcher).also {
            stores += ViewModelStore().apply { put("test", it) }
        }

    @Test fun onlyExplicitLibraryIsRead() = runTest(dispatcher) {
        var id = ""
        val vm = vm(LibraryShellRepository { id = it; ready() })
        vm.refresh(); advanceUntilIdle()
        assertEquals("lib", id); assertEquals(3L, vm.state.value.copies)
    }
    @Test fun wrongLibraryResponseIsNeverPublished() = runTest(dispatcher) {
        val vm = vm(LibraryShellRepository { ready("other") })
        vm.refresh(); advanceUntilIdle()
        assertEquals(ShellReadFailure.LIBRARY_CHANGED, vm.state.value.failure)
        assertTrue(vm.state.value.rows.isEmpty())
    }
    @Test fun newerRefreshWinsEvenIfOldReadIgnoresCancellation() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val vm = vm(LibraryShellRepository {
            if (++reads == 1) withContext(NonCancellable) { gate.await(); ready(rowId = "old") }
            else ready(rowId = "fresh")
        })
        vm.refresh(); runCurrent()
        vm.refresh(); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals("fresh", vm.state.value.rows.single().id)
    }
    @Test fun errorsClearStaleRowsAndRetryRecovers() = runTest(dispatcher) {
        var failure = false
        val vm = vm(LibraryShellRepository { if (failure) error("unreadable") else ready() })
        vm.refresh(); advanceUntilIdle()
        failure = true; vm.refresh(); advanceUntilIdle()
        assertTrue(vm.state.value.rows.isEmpty())
        assertNotNull(vm.state.value.failure)
        failure = false; vm.refresh(); advanceUntilIdle()
        assertEquals(3L, vm.state.value.copies)
    }
    @Test fun filtersRestoreAndRemovedGroupReturnsToDirectory() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        var result = ready()
        val repository = LibraryShellRepository { result }
        val original = vm(repository, saved)
        original.section(LibrarySection.GROUPS); original.membership("Grupo"); original.query("Test")
        val restored = vm(repository, saved)
        restored.refresh(); advanceUntilIdle()
        assertEquals("Grupo", restored.state.value.options.membership)
        assertEquals("Test", restored.state.value.options.query)
        result = result.copy(snapshot = result.snapshot.copy(collection = CollectionSnapshot("lib", emptyList())))
        restored.refresh(); advanceUntilIdle()
        assertNull(restored.state.value.options.membership)
        assertTrue(restored.state.value.showMemberships)
    }

    @Test fun latePublicationAfterReturnReloadsByIdentityWithoutDuplicates() = runTest(dispatcher) {
        val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var result = ready(rowId = "before")
        var observedId = ""
        val repository = object : LibraryShellRepository {
            override suspend fun read(libraryId: String) = result
            override fun changes(libraryId: String) = events.also { observedId = libraryId }
        }
        val model = vm(repository)
        val observation = backgroundScope.launch(dispatcher) { model.observeChanges() }
        model.refresh(); runCurrent()
        assertEquals("lib", observedId)
        assertEquals("before", model.state.value.rows.single().id)
        result = ready(rowId = "after")
        events.emit(Unit); runCurrent()
        assertEquals(listOf("after"), model.state.value.rows.map { it.id })
        events.emit(Unit); runCurrent()
        assertEquals(1, model.state.value.rows.size)
        observation.cancel(); runCurrent()
        result = ready(rowId = "hidden")
        events.emit(Unit); runCurrent()
        assertEquals("after", model.state.value.rows.single().id)
        model.refresh(); runCurrent()
        assertEquals("hidden", model.state.value.rows.single().id)
    }
}
