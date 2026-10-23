package io.asv.mtgocr.ocrreader.data.legacy

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PrivateLibraryFileReaderTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun missingAndDirectoryAreNotReportedAsEmptyFiles() {
        val root = temp.newFolder("private")
        File(root, "directory").mkdir()
        val before = root.list()!!.toList()
        val reader = PrivateLibraryFileReader { root }
        assertNull(reader.bytes("missing"))
        assertNull(reader.bytes("directory"))
        assertEquals(before, root.list()!!.toList())
    }

    @Test fun zeroLengthAndBackupSizesRemainIndependentWithoutRepair() {
        val root = temp.newFolder("private")
        File(root, "library").writeBytes(byteArrayOf())
        val backup = File(root, "library.bak").apply { writeBytes(byteArrayOf(0, 1, -1, 2)) }
        val reader = PrivateLibraryFileReader { root }
        assertEquals(0L, reader.bytes("library"))
        assertEquals(4L, reader.bytes("library.bak"))
        assertArrayEquals(byteArrayOf(0, 1, -1, 2), backup.readBytes())
        assertArrayEquals(byteArrayOf(), File(root, "library").readBytes())
    }

    @Test fun rejectsTraversalAndNestedFilesEvenWhenTheyExist() {
        val root = temp.newFolder("private")
        val outside = temp.newFile("outside").apply { writeText("outside") }
        val nested = File(root, "nested/file").apply { parentFile!!.mkdirs(); writeText("inside") }
        val reader = PrivateLibraryFileReader { root }
        for (name in listOf("../outside", "nested/file", "nested/missing", "..")) {
            try { reader.bytes(name); fail("path should be rejected: $name") }
            catch (error: IllegalArgumentException) { assertEquals("Library file outside private root", error.message) }
        }
        assertEquals("outside", outside.readText())
        assertEquals("inside", nested.readText())
    }

    @Test fun canonicalDirectChildPathsKeepExistingBehavior() {
        val root = temp.newFolder("private")
        File(root, "nested").mkdir()
        File(root, "library").writeText("123")
        val reader = PrivateLibraryFileReader { root }
        assertEquals(3L, reader.bytes("./library"))
        assertEquals(3L, reader.bytes("nested/../library"))
    }

    @Test fun rootLookupIsLazyAndRefreshedEveryCall() {
        val first = temp.newFolder("first")
        val second = temp.newFolder("second")
        File(first, "library").writeText("1")
        File(second, "library").writeText("12")
        var root = first
        var calls = 0
        val reader = PrivateLibraryFileReader { calls++; root }
        assertEquals(0, calls)
        assertEquals(1L, reader.bytes("library"))
        root = second
        assertEquals(2L, reader.bytes("library"))
        assertEquals(2, calls)
    }

    @Test fun missingRootIsNeverCreated() {
        val root = File(temp.root, "absent")
        assertNull(PrivateLibraryFileReader { root }.bytes("library"))
        assertFalse(root.exists())
    }

    @Test(expected = IOException::class)
    fun rootErrorsAreNotConvertedToMissingOrZero() {
        PrivateLibraryFileReader { throw IOException("unavailable") }.bytes("library")
    }
}
