package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.photos.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadOnlyPhotoReaderTest {
    private class Source : PhotoReadSource {
        val calls = mutableListOf<String>()
        var evidenceFailure: Exception? = null
        var importFailure: Exception? = null
        var evidence = PhotoSection(listOf(PhotoRecord("e", "Evidence", null, "metadata")))
        val imports = PhotoSection(listOf(PhotoRecord("i", "Import", "/local/image", "details")))
        override fun evidence(libraryId: String): PhotoSection {
            calls += libraryId
            evidenceFailure?.let { throw it }
            return evidence
        }
        override fun imports(): PhotoSection {
            calls += "imports"
            importFailure?.let { throw it }
            return imports
        }
    }

    @Test fun explicitLibraryThenGlobalImportsPreserveProjections() = runTest {
        val source = Source()
        val result = ReadOnlyPhotoReader(source, StandardTestDispatcher(testScheduler)).read("extra")
        assertEquals(listOf("extra", "imports"), source.calls)
        assertSame(source.evidence, result.evidence)
        assertSame(source.imports, result.imports)
    }

    @Test fun evidenceExceptionDoesNotHideImports() = runTest {
        val source = Source().apply { evidenceFailure = IllegalStateException("broken") }
        val result = ReadOnlyPhotoReader(source, StandardTestDispatcher(testScheduler)).read("missing")
        assertEquals(PhotoSection(failed = true), result.evidence)
        assertSame(source.imports, result.imports)
        assertEquals(listOf("missing", "imports"), source.calls)
    }

    @Test fun importExceptionDoesNotHideEvidence() = runTest {
        val source = Source().apply { importFailure = SecurityException("denied") }
        val result = ReadOnlyPhotoReader(source, StandardTestDispatcher(testScheduler)).read("default")
        assertSame(source.evidence, result.evidence)
        assertEquals(PhotoSection(failed = true), result.imports)
    }

    @Test fun bothFailuresRemainIndependent() = runTest {
        val source = Source().apply {
            evidenceFailure = IllegalArgumentException()
            importFailure = IllegalStateException()
        }
        val result = ReadOnlyPhotoReader(source, StandardTestDispatcher(testScheduler)).read("extra")
        assertEquals(PhotoSnapshot(PhotoSection(failed = true), PhotoSection(failed = true)), result)
        assertEquals(listOf("extra", "imports"), source.calls)
    }

    @Test fun constructionIsLazyAndRefreshUsesDispatcherWithoutCaching() = runTest {
        val source = Source()
        val reader = ReadOnlyPhotoReader(source, StandardTestDispatcher(testScheduler))
        assertTrue(source.calls.isEmpty())
        val first = async(start = CoroutineStart.UNDISPATCHED) { reader.read("first") }
        assertTrue(source.calls.isEmpty())
        advanceUntilIdle()
        assertSame(source.evidence, first.await().evidence)
        source.evidence = PhotoSection()
        assertEquals(PhotoSection(), reader.read("second").evidence)
        assertEquals(listOf("first", "imports", "second", "imports"), source.calls)
    }

    @Test fun reportedFailureIsNotConvertedIntoSuccessfulEmptySection() = runTest {
        val source = Source().apply { evidence = PhotoSection(failed = true) }
        val result = ReadOnlyPhotoReader(source, StandardTestDispatcher(testScheduler)).read("extra")
        assertTrue(result.evidence.failed)
        assertFalse(result.imports.failed)
    }
}
