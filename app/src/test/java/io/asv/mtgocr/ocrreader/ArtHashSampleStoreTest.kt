package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtHashSampleStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun pruneKeepsOnlyThirtyNewestJpegs() {
        val directory = temporaryFolder.newFolder()
        repeat(32) { index -> directory.resolve("%013d-test.jpg".format(index)).writeText("image") }
        directory.resolve("note.txt").writeText("keep")

        ArtHashSampleStore.prune(directory)

        assertEquals(30, directory.listFiles { file -> file.extension == "jpg" }?.size)
        assertFalse(directory.resolve("%013d-test.jpg".format(0)).exists())
        assertFalse(directory.resolve("%013d-test.jpg".format(1)).exists())
        assertTrue(directory.resolve("%013d-test.jpg".format(31)).exists())
        assertTrue(directory.resolve("note.txt").exists())
    }
}
