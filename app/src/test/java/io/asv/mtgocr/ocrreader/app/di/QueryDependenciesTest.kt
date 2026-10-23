package io.asv.mtgocr.ocrreader.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.asv.mtgocr.ocrreader.domain.photos.*
import io.asv.mtgocr.ocrreader.domain.services.*
import io.asv.mtgocr.ocrreader.feature.photos.PhotoViewModel
import io.asv.mtgocr.ocrreader.feature.services.ServicesViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueryDependenciesTest {
    private class OtherModel : ViewModel()

    @Test fun constructionAndWrongModelDoNotCreateReaders() {
        var calls = 0
        val photo = PhotoDependencies({ calls++; error("unexpected") }, "extra")
        val services = ServicesDependencies { calls++; error("unexpected") }
        for (factory in listOf(photo.factory, services.factory)) {
            try { factory.create(OtherModel::class.java); fail("wrong type accepted") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, calls)
    }

    @Test fun providerRetainsModelAndCreatesOnlyOncePerStore() {
        var calls = 0
        val dependencies = PhotoDependencies({
            calls++
            PhotoReader { error("no eager read") }
        }, "extra")
        val store = ViewModelStore()
        try {
            val provider = ViewModelProvider(store, dependencies.factory)
            assertSame(provider[PhotoViewModel::class.java], provider[PhotoViewModel::class.java])
            assertEquals(1, calls)
        } finally { store.clear() }
    }

    @Test fun independentStoresDoNotShareModels() {
        val dependencies = ServicesDependencies { ServicesReader { error("no eager read") } }
        val first = ViewModelStore(); val second = ViewModelStore()
        try {
            assertNotSame(ViewModelProvider(first, dependencies.factory)[ServicesViewModel::class.java],
                ViewModelProvider(second, dependencies.factory)[ServicesViewModel::class.java])
        } finally { first.clear(); second.clear() }
    }

    @Test fun photoFactoryInjectsReaderAndExplicitLibraryWithoutReadingAtCreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val calls = mutableListOf<String>()
            val expected = PhotoSnapshot(PhotoSection(), PhotoSection(failed = true))
            val dependencies = PhotoDependencies({ PhotoReader { calls += it; expected } }, "extra")
            val model = ViewModelProvider(store, dependencies.factory)[PhotoViewModel::class.java]
            assertTrue(calls.isEmpty())
            model.refresh(); advanceUntilIdle()
            assertEquals(listOf("extra"), calls)
            assertEquals(expected, model.state.value.snapshot)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun servicesFactoryInjectsReaderWithoutStartingWorkAtCreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var calls = 0
            val dependencies = ServicesDependencies { ServicesReader { calls++; throw IllegalStateException("fixture") } }
            val model = ViewModelProvider(store, dependencies.factory)[ServicesViewModel::class.java]
            assertEquals(0, calls)
            model.refresh(); advanceUntilIdle()
            assertEquals(1, calls)
            assertTrue(model.state.value.failed)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
