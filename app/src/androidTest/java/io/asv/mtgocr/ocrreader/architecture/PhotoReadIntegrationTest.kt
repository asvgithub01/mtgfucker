package io.asv.mtgocr.ocrreader.architecture

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.PhotoScanEntry
import io.asv.mtgocr.ocrreader.data.legacy.LegacyPhotoReader
import io.asv.mtgocr.ocrreader.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ObjectOutputStream
import java.util.UUID

class PhotoReadIntegrationTest {
    @Test fun queriesWithoutWritingRepairingOrRemovingAnyEvidence() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "photos-test-" + UUID.randomUUID()
        val root = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        fun write(name: String, value: Any) {
            ObjectOutputStream(File(root, name).outputStream()).use { it.writeObject(value) }
        }
        fun snapshot() = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes().toList() }
        try {
            val photo = File(root, "photo_scans/a.image").apply { parentFile!!.mkdirs(); writeText("image fixture") }
            val evidence = File(root, "scan_evidence/card/a.jpg").apply { parentFile!!.mkdirs(); writeText("evidence fixture") }
            val card = CardInfo("Synthetic", "", "", "", "3").apply {
                addScanEvidence("metadata one", "scan_evidence/card/a.jpg")
                scanMetadataHistory.add("metadata without photo")
            }
            write(LibraryCatalog.DEFAULT_FILE, Biblio(LibraryCatalog.DEFAULT_FILE, "Test").apply { addCard(card) })
            write("photoScans.bin.bak", arrayListOf(PhotoScanEntry(id = "photo", imagePath = photo.absolutePath)))
            context.getSharedPreferences("premium_access", 0).edit().putBoolean("premium_enabled", true).commit()
            context.getSharedPreferences("library_catalog", 0).edit()
                .putStringSet("libraries", setOf("extra\tExtra\tbiblio_extra.Json"))
                .putString("active_library_id", "default").commit()
            write("biblio_extra.Json.bak", Biblio("biblio_extra.Json", "Extra").apply {
                addCard(CardInfo("Extra", "", "", "", "1").apply {
                    addScanEvidence("extra metadata", "scan_evidence/card/a.jpg")
                })
            })
            File(root, "biblio_extra.Json").writeText("broken extra primary")
            val prefsBefore = listOf("library_catalog", "premium_access").associateWith {
                context.getSharedPreferences(it, 0).all.toMap()
            }
            val reader = LegacyPhotoReader(context)
            val before = snapshot()
            val result = reader.read("default")
            assertFalse(result.evidence.failed)
            assertFalse(result.imports.failed)
            assertNull(result.evidence.records.first().path)
            assertEquals(evidence.canonicalPath, result.evidence.records.last().path)
            assertEquals(photo.canonicalPath, result.imports.records.single().path)
            assertEquals(before, snapshot())
            val extra = reader.read("extra")
            assertFalse(extra.evidence.failed)
            assertEquals("Extra \u00b7 1", extra.evidence.records.single().title)
            assertEquals(result.imports, extra.imports)
            assertEquals(before, snapshot())
            assertEquals(prefsBefore, prefsBefore.keys.associateWith {
                context.getSharedPreferences(it, 0).all.toMap()
            })
            File(root, "photoScans.bin").writeText("corrupt")
            val corrupt = snapshot()
            assertFalse(reader.read("default").imports.failed)
            assertEquals(corrupt, snapshot())
            File(root, "photoScans.bin.bak").writeText("corrupt backup")
            val broken = snapshot()
            assertTrue(reader.read("default").imports.failed)
            assertFalse(reader.read("default").evidence.failed)
            assertTrue(reader.read("unknown").evidence.failed)
            assertEquals(broken, snapshot())
        } finally {
            root.deleteRecursively()
            listOf("library_catalog", "premium_access").forEach { base.deleteSharedPreferences(prefix + it) }
        }
    }
}
