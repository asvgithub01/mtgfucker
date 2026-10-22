package io.asv.mtgocr.ocrreader.architecture

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.data.legacy.LegacyLibraryShellRepository
import io.asv.mtgocr.ocrreader.domain.library.*
import io.asv.mtgocr.ocrreader.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ObjectOutputStream
import java.util.UUID

class LibraryShellIntegrationTest {
    @Test fun readsExplicitNonDefaultFilesWithoutTouchingOtherLibraryOrPreferences() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "shell-nondefault-" + UUID.randomUUID()
        val root = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences(prefix + name, mode)
        }
        val prefNames = listOf("library_catalog", "premium_access")
        fun preferences() = prefNames.associateWith { context.getSharedPreferences(it, 0).all.toMap() }
        fun files() = root.listFiles().orEmpty().associate { it.name to it.readBytes().toList() }
        fun fixture(name: String, value: Any) {
            ObjectOutputStream(File(root, name).outputStream()).use { it.writeObject(value) }
        }
        try {
            context.getSharedPreferences("premium_access", 0).edit().putBoolean("premium_enabled", true).commit()
            context.getSharedPreferences("library_catalog", 0).edit()
                .putStringSet("libraries", setOf("extra\tExtra\tbiblio_extra.Json"))
                .putString("active_library_id", "extra").commit()
            File(root, LibraryCatalog.DEFAULT_FILE).writeText("other-library-untouched")
            fixture("biblio_extra.Json", Biblio("biblio_extra.Json", "Extra").apply {
                addCard(CardInfo("Synthetic extra", "", "", "", "2"))
            })
            fixture("biblio_extra_DeckCatalog.Json.bak", DeckCatalog().apply {
                decks.add(DeckDefinition("Extra deck", "free", 7))
            })
            File(root, "biblio_extra_DeckCatalog.Json").writeText("broken-primary-do-not-repair")
            val beforeFiles = files()
            val beforePrefs = preferences()
            val read = LegacyLibraryShellRepository(context, Dispatchers.IO).read("extra") as ShellReadResult.Ready
            assertEquals("extra", read.snapshot.collection.libraryId)
            assertEquals("Synthetic extra", read.snapshot.collection.rows.single().name)
            assertEquals("Extra deck", read.snapshot.decks.single().name)
            assertEquals(beforeFiles, files())
            assertEquals(beforePrefs, preferences())
        } finally {
            root.deleteRecursively()
            prefNames.forEach { base.deleteSharedPreferences(prefix + it) }
        }
    }

    @Test fun readsGroupsAndEmptyDecksWithoutMigratingOrRepairingFiles() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "shell-test-" + UUID.randomUUID()
        val root = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences(prefix + name, mode)
        }
        fun write(name: String, value: Any) {
            ObjectOutputStream(File(root, name).outputStream()).use { it.writeObject(value) }
        }
        try {
            val repository = LegacyLibraryShellRepository(context, Dispatchers.IO)
            val card = CardInfo("Synthetic", "", "", "", "3").apply { addGroup("Group only") }
            write(LibraryCatalog.DEFAULT_FILE, Biblio(LibraryCatalog.DEFAULT_FILE, "Test").apply { addCard(card) })
            val inventory = File(root, LibraryCatalog.DEFAULT_FILE).readBytes()
            val missing = repository.read("default") as ShellReadResult.Ready
            assertTrue(missing.snapshot.decks.isEmpty())
            assertFalse(File(root, "myDeckCatalog.Json").exists())
            write("myDeckCatalog.Json", DeckCatalog().apply { decks.add(DeckDefinition("Empty", "free", 1)) })
            val deck = File(root, "myDeckCatalog.Json")
            val original = deck.readBytes()
            File(root, "myDeckCatalog.Json.bak").writeBytes(original)
            val read = repository.read("default") as ShellReadResult.Ready
            assertEquals(listOf("Group only"), read.snapshot.collection.rows.single().groups)
            assertTrue(read.snapshot.collection.rows.single().decks.isEmpty())
            assertEquals("Empty", read.snapshot.decks.single().name)
            assertArrayEquals(inventory, File(root, LibraryCatalog.DEFAULT_FILE).readBytes())
            assertArrayEquals(original, deck.readBytes())
            deck.writeText("broken")
            assertTrue(repository.read("default") is ShellReadResult.Ready)
            assertEquals("broken", deck.readText())
            assertArrayEquals(original, File(root, "myDeckCatalog.Json.bak").readBytes())
            File(root, "myDeckCatalog.Json.bak").writeText("broken backup")
            assertEquals(ShellReadResult.Failed(ShellReadFailure.DECKS_UNREADABLE), repository.read("default"))
            assertEquals(ShellReadResult.Failed(ShellReadFailure.LIBRARY_CHANGED), repository.read("other"))
            assertArrayEquals(inventory, File(root, LibraryCatalog.DEFAULT_FILE).readBytes())
        } finally {
            root.deleteRecursively()
            listOf("library_catalog", "premium_access").forEach { base.deleteSharedPreferences(prefix + it) }
        }
    }
}
