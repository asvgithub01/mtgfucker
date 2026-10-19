package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class PostprocessCleanupTest {
    @Test fun onlyFullyReviewedOrDeletedPhotosAreEligible() {
        assertTrue(PostprocessCleanup.eligible(false,emptyList()))
        assertTrue(PostprocessCleanup.eligible(true,listOf("reviewed","reviewed")))
        assertFalse(PostprocessCleanup.eligible(true,emptyList()))
        for(state in listOf("pending","conflict","inconclusive","consistent","suggestion"))
            assertFalse(PostprocessCleanup.eligible(true,listOf("reviewed",state)))
    }
    @Test fun exactFamilyRejectsPathsAndDoesNotDeleteDirectories() {
        val dir=Files.createTempDirectory("postprocess-cleanup").toFile()
        try {
            val key="12345678-1234-1234-1234-123456789abc"
            File(dir,"$key.jpg").writeText("photo")
            File(dir,"$key.ocr.json").writeText("{}")
            File(dir,"$key-other.jpg").writeText("unrelated")
            File(dir,"$key.folder").mkdir()
            assertEquals(setOf("$key.jpg","$key.ocr.json"),PostprocessCleanup.family(dir,key).map { it.name }.toSet())
            assertTrue(PostprocessCleanup.family(dir,"../$key").isEmpty())
            assertTrue(PostprocessCleanup.family(dir,"").isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
