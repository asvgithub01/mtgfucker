package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class StoragePolicyTest {
    @Test fun urlVariantsKeepArtworkAndPrintingTogether() {
        val image="https://cards.scryfall.io/normal/front/a/b/card.jpg"
        val names=StorageData.imageNames(image)
        assertTrue(StorageData.hash(image) in names)
        assertTrue(StorageData.hash(LaunchArtworkUrl.resolve(image)!!) in names)
        assertEquals(12,names.size)
    }
    @Test fun pathsCannotEscapeStorageRoot() {
        val root=File(System.getProperty("java.io.tmpdir"),"storage-safe")
        assertTrue(StorageData.safeChild(root,File(root,"image.jpg")))
        assertFalse(StorageData.safeChild(root,File(root,"../outside.jpg")))
        assertFalse(StorageData.safeChild(root,root))
    }
}
