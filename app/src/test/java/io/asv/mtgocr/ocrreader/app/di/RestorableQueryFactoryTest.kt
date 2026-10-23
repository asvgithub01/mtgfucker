package io.asv.mtgocr.ocrreader.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import io.asv.mtgocr.ocrreader.domain.catalog.SetSearchMatcher
import io.asv.mtgocr.ocrreader.domain.library.LibraryShellRepository
import org.junit.Assert.*
import org.junit.Test

class RestorableQueryFactoryTest {
    private class Model : ViewModel()
    private class Other : ViewModel()

    @Test fun originalExtrasAreForwardedAndConstructionIsDeferred() {
        var seen: CreationExtras? = null
        val model = Model()
        val factory = RestorableQueryViewModelFactory(Model::class.java) { seen = it; model }
        assertNull(seen)
        val extras = MutableCreationExtras()
        assertSame(model, factory.create(Model::class.java, extras))
        assertSame(extras, seen)
    }

    @Test fun wrongClassNeverInvokesBuilder() {
        val factory = RestorableQueryViewModelFactory(Model::class.java) { error("unexpected") }
        try { factory.create(Other::class.java, CreationExtras.Empty); fail("accepted wrong class") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun concreteFactoriesDoNotReadOrResolveCatalogForWrongModel() {
        var calls = 0
        val catalog = SetCatalogViewModelFactory({ calls++; error("unexpected") }, SetSearchMatcher { _, _ -> true })
        val shell = LibraryShellDependencies(LibraryShellRepository { calls++; error("unexpected") }, "extra")
        for (factory in listOf(catalog, shell.factory)) {
            try { factory.create(Other::class.java, CreationExtras.Empty); fail("accepted wrong class") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, calls)
    }
}
