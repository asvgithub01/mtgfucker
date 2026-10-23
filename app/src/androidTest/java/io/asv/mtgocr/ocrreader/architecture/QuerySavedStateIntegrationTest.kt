package io.asv.mtgocr.ocrreader.architecture

import android.os.Bundle
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.*
import io.asv.mtgocr.ocrreader.app.di.*
import io.asv.mtgocr.ocrreader.domain.catalog.*
import io.asv.mtgocr.ocrreader.domain.library.*
import io.asv.mtgocr.ocrreader.feature.catalog.SetCatalogViewModel
import io.asv.mtgocr.ocrreader.feature.library.LibraryShellViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** Fresh owners and Stores exercise Bundle restoration, not just retained ViewModel instances. */
class QuerySavedStateIntegrationTest {
    @Test fun missingOwnersFailRatherThanCreatingAnEmptyHandle() = runBlocking {
        withContext(Dispatchers.Main) {
            val shell = LibraryShellDependencies(LibraryShellRepository { error("must not read") }, "extra")
            val catalog = SetCatalogViewModelFactory({ SetCatalogReader { error("must not read") } },
                SetSearchMatcher { _, _ -> true })
            try {
                shell.factory.create(LibraryShellViewModel::class.java, androidx.lifecycle.viewmodel.CreationExtras.Empty)
                fail("missing owners accepted")
            } catch (_: IllegalArgumentException) { }
            try {
                catalog.create(SetCatalogViewModel::class.java, androidx.lifecycle.viewmodel.CreationExtras.Empty)
                fail("missing owners accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    private class Owner(restored: Bundle? = null) : SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        init {
            controller.performAttach()
            enableSavedStateHandles()
            controller.performRestore(restored)
            registry.currentState = Lifecycle.State.CREATED
        }
        fun extras(defaults: Bundle = Bundle()) = MutableCreationExtras().apply {
            this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@Owner
            this[VIEW_MODEL_STORE_OWNER_KEY] = this@Owner
            this[DEFAULT_ARGS_KEY] = defaults
        }
        fun save() = Bundle().also(controller::performSave)
        fun close() { viewModelStore.clear(); registry.currentState = Lifecycle.State.DESTROYED }
    }

    @Test fun shellRestoresOptionsIntoNewStoreAndKeepsExplicitLibrary() = runBlocking {
        withContext(Dispatchers.Main) {
            val calls = mutableListOf<String>()
            val repository = LibraryShellRepository {
                calls += it
                ShellReadResult.Failed(ShellReadFailure.COLLECTION_UNREADABLE)
            }
            val first = Owner()
            val restored: Bundle
            val original: LibraryShellViewModel
            try {
                val provider = ViewModelProvider(first.viewModelStore,
                    LibraryShellDependencies(repository, "extra").factory, first.extras())
                original = provider[LibraryShellViewModel::class.java]
                assertSame(original, provider[LibraryShellViewModel::class.java])
                assertTrue(calls.isEmpty())
                original.section(LibrarySection.GROUPS)
                original.membership("My group")
                original.query("localized")
                original.sort(LibrarySort.NAME)
                restored = first.save()
            } finally { first.close() }
            val second = Owner(restored)
            try {
                val defaults = Bundle().apply { putString("query", "wrong default") }
                val model = ViewModelProvider(second.viewModelStore,
                    LibraryShellDependencies(repository, "extra").factory, second.extras(defaults))[LibraryShellViewModel::class.java]
                assertNotSame(original, model)
                val options = model.state.value.options
                assertEquals(LibrarySection.GROUPS, options.section)
                assertEquals("My group", options.membership)
                assertEquals("localized", options.query)
                assertEquals(LibrarySort.NAME, options.sort)
                model.refresh()
                withTimeout(5000) { model.state.first { !it.loading } }
                assertEquals(listOf("extra"), calls)
            } finally { second.close() }
        }
    }

    @Test fun catalogRestoresQueryAndSelectionWithoutEagerProviderResolution() = runBlocking {
        withContext(Dispatchers.Main) {
            var providers = 0
            var reads = 0
            val set = SetSummary("SET", "Set", "2020", 1)
            fun factory() = SetCatalogViewModelFactory({
                providers++
                SetCatalogReader { reads++; listOf(set) }
            }, SetSearchMatcher { _, _ -> true })
            val first = Owner()
            val restored: Bundle
            val original: SetCatalogViewModel
            try {
                val factory = factory()
                assertEquals(0, providers)
                val provider = ViewModelProvider(first.viewModelStore, factory, first.extras())
                original = provider[SetCatalogViewModel::class.java]
                withTimeout(5000) { original.state.first { !it.loading } }
                original.query("saved query")
                original.select("SET")
                assertSame(original, provider[SetCatalogViewModel::class.java])
                assertEquals(1, providers)
                restored = first.save()
            } finally { first.close() }
            val second = Owner(restored)
            try {
                val model = ViewModelProvider(second.viewModelStore, factory(), second.extras())[SetCatalogViewModel::class.java]
                assertNotSame(original, model)
                val state = withTimeout(5000) { model.state.first { !it.loading } }
                assertEquals("saved query", state.query)
                assertEquals("SET", state.selected?.code)
                assertEquals(2, providers)
                assertEquals(2, reads)
            } finally { second.close() }
        }
    }
}
