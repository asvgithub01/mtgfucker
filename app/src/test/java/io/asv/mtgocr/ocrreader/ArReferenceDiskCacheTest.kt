package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArReferenceDiskCacheTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun roundTripAcrossCacheInstancesAndDiscard() {
        val dir=temporary.newFolder()
        ArReferenceDiskCache(dir).write("uuid","url",byteArrayOf(1,2,3))
        val reopened=ArReferenceDiskCache(dir)
        assertArrayEquals(byteArrayOf(1,2,3),reopened.read("uuid","url"))
        reopened.discard("uuid","url")
        assertNull(reopened.read("uuid","url"))
    }
    @Test fun keysSeparatePrintingAndImageAndStayInsideDirectory() {
        val dir=temporary.newFolder(); val cache=ArReferenceDiskCache(dir)
        assertNotEquals(cache.file("a","url"),cache.file("b","url"))
        assertNotEquals(cache.file("a","url"),cache.file("a","other"))
        assertEquals(dir.canonicalFile,cache.file("../escape","../../x").parentFile.canonicalFile)
    }
    @Test fun boundedToThirtyTwoEntries() {
        val dir=temporary.newFolder(); val cache=ArReferenceDiskCache(dir)
        repeat(40) { cache.write("p$it","url",byteArrayOf(1)); cache.file("p$it","url").setLastModified(1000L+it) }
        assertTrue(dir.listFiles()!!.size<=32)
    }
    @Test fun oversizedEntryIsNotStored() {
        val dir=temporary.newFolder(); val cache=ArReferenceDiskCache(dir)
        cache.write("p","url",ByteArray(8*1024*1024+1))
        assertNull(cache.read("p","url"))
    }
}
