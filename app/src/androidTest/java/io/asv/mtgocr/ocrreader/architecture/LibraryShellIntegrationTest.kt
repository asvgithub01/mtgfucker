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
