package io.asv.mtgocr.ocrreader.data.legacy

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class PhotoReadPolicyTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun keepsOriginalIndicesForUnequalHistories() {
        val rows = evidenceRecords("card", "Name", listOf("first", "second"), listOf("photo")) { it }
        assertEquals(listOf("card:1", "card:0"), rows.map { it.id })
        assertNull(rows[0].path)
        assertEquals("second", rows[0].details)
        assertEquals("photo", rows[1].path)
        assertEquals("", evidenceRecords("card", "Name", emptyList(), listOf("photo")) { it }.single().details)
    }
    @Test fun resolvesOnlyExistingPhotosInsideTheirOwnRoot() {
        val root = temporary.root
        val evidence = File(root, "scan_evidence/card/a.jpg").apply { parentFile!!.mkdirs(); writeText("fixture") }
        val imported = File(root, "photo_scans/a.image").apply { parentFile!!.mkdirs(); writeText("fixture") }
        assertEquals(evidence.canonicalFile, PhotoPathPolicy.resolve(root, "scan_evidence/card/a.jpg", false))
        assertEquals(imported.canonicalFile, PhotoPathPolicy.resolve(root, imported.absolutePath, true))
        assertNull(PhotoPathPolicy.resolve(root, evidence.absolutePath, true))
        assertNull(PhotoPathPolicy.resolve(root, evidence.absolutePath, false))
        assertNull(PhotoPathPolicy.resolve(root, "scan_evidence/../photo_scans/a.image", false))
        assertNull(PhotoPathPolicy.resolve(root, "scan_evidence/card", false))
        assertNull(PhotoPathPolicy.resolve(root, "scan_evidence/missing.jpg", false))
        assertNull(PhotoPathPolicy.resolve(root, "https://example.com/a.jpg", false))
        assertEquals("fixture", evidence.readText())
        assertEquals("fixture", imported.readText())
    }
}
