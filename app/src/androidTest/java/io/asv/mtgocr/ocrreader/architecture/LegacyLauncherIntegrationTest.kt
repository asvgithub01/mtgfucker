package io.asv.mtgocr.ocrreader.architecture

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.*
import io.asv.mtgocr.ocrreader.data.legacy.LegacyLauncherRepository
import io.asv.mtgocr.ocrreader.app.di.CollectionDependencies
import io.asv.mtgocr.ocrreader.domain.collection.CollectionReadResult
import io.asv.mtgocr.ocrreader.legacybridge.LegacyLibraryNavigation
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ObjectOutputStream
import java.util.UUID

class LegacyLauncherIntegrationTest {
    /** Fresh isolated namespace, including applicationContext; never user data or cloud setup. */
    private fun isolated(block: (Context) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "compose-test-" + UUID.randomUUID()
        val root = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = root
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences(prefix + name, mode)
        }
        try { block(context) } finally {
            root.deleteRecursively()
            listOf("library_catalog", "premium_access").forEach { base.deleteSharedPreferences(prefix + it) }
        }
    }

    private fun write(file: File, value: Biblio) {
        ObjectOutputStream(file.outputStream()).use { it.writeObject(value) }
    }

    @Test fun primaryBackupAndCorruptionNeverWriteFiles() = isolated { context ->
        runBlocking {
            val reader = CollectionDependencies(context).collectionReader
            assertEquals(CollectionReadResult.Missing, reader.read("default"))
            val file = File(context.filesDir, LibraryCatalog.DEFAULT_FILE)
            val value = Biblio(file.name, "Synthetic").apply {
                addCard(CardInfo("Test", "", "", "", "2"))
            }
            write(file, value)
            val original = file.readBytes()
            assertTrue(reader.read("default") is CollectionReadResult.Ready)
            assertArrayEquals(original, file.readBytes())
            File(file.path + ".bak").writeBytes(original)
            file.writeText("corrupt-primary")
            assertTrue(reader.read("default") is CollectionReadResult.Ready)
            assertEquals("corrupt-primary", file.readText())
            assertArrayEquals(original, File(file.path + ".bak").readBytes())
            File(file.path + ".bak").writeText("corrupt-backup")
            assertTrue(reader.read("default") is CollectionReadResult.Failed)
            val launcher = LegacyLauncherRepository(context, Dispatchers.IO)
            assertFalse(launcher.load().readable)
            assertEquals("corrupt-primary", file.readText())
            assertEquals("corrupt-backup", File(file.path + ".bak").readText())
        }
    }

    @Test fun premiumSelectionPinAndDefaultFallbackPreserveCollectionBytes() = isolated { context ->
        runBlocking {
            // Set synthetic entitlement directly; do NOT invoke cloud activation.
            context.getSharedPreferences("premium_access", 0).edit().putBoolean("premium_enabled", true).commit()
            context.getSharedPreferences("library_catalog", 0).edit()
                .putStringSet("libraries", setOf("extra\tExtra\tbiblio_extra.Json"))
                .putString("active_library_id", "extra").commit()
            val card = CardInfo("Pinned", "", "", "https://example.invalid/art.jpg", "3")
            val file = File(context.filesDir, "biblio_extra.Json")
            write(file, Biblio(file.name, "Extra").apply { addCard(card) })
            val original = file.readBytes()
            LibraryCatalog.pinBackground(context, card.collectionItemId)
            val repository = LegacyLauncherRepository(context, Dispatchers.IO)
            val loaded = repository.load()
            assertEquals(2, loaded.libraries.size)
            assertEquals("extra", loaded.selectedId)
            assertEquals(card.imgPath, loaded.backgroundUrl)
            assertEquals("default", repository.select("default").selectedId)
            assertEquals("extra", repository.select("extra").selectedId)
            assertArrayEquals(original, file.readBytes())
            context.getSharedPreferences("premium_access", 0).edit().putBoolean("premium_enabled", false).commit()
            assertEquals("default", repository.load().selectedId)
            assertEquals(1, repository.load().libraries.size)
            assertArrayEquals(original, file.readBytes())
        }
    }

    @Test fun navigationPreservesLegacyExtras() = isolated { context ->
        val intent = LegacyLibraryNavigation.intent(context, true)
        assertEquals(OcrCaptureActivity::class.java.name, intent.component!!.className)
        assertEquals("0", intent.getStringExtra(App.INTENT_PERSISTOR_MODE))
        assertEquals(ScannerSettings.autoFocus(context), intent.getBooleanExtra(App.INTENT_AUTO_FOCUS, false))
        assertEquals(ScannerSettings.flash(context), intent.getBooleanExtra(App.INTENT_USE_FLASH, false))
        assertTrue(intent.getBooleanExtra("askStartupPriceRefresh", false))
        assertFalse(LegacyLibraryNavigation.intent(context, false).getBooleanExtra("askStartupPriceRefresh", true))
    }
}
