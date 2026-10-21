package io.asv.mtgocr.ocrreader.architecture

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.*
import io.asv.mtgocr.ocrreader.data.legacy.LegacyLibraryShellRepository
import io.asv.mtgocr.ocrreader.domain.library.ShellReadResult
import io.asv.mtgocr.ocrreader.legacybridge.*
import io.asv.mtgocr.ocrreader.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ObjectOutputStream
import java.util.UUID

class LibraryReturnIntegrationTest {
    @Test fun lateAtomicPublicationRefreshesOnlyPinnedLibraryWithoutWriting() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "return-test-" + UUID.randomUUID()
        val root = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences(prefix + name, mode)
        }
        val repository = LegacyLibraryShellRepository(context, Dispatchers.IO)
        val events = Channel<Unit>(Channel.UNLIMITED)
        val observation = launch(Dispatchers.IO) { repository.changes("default").collect { events.send(Unit) } }
        try {
            withTimeout(5000) { events.receive() } // registered, initial invalidation
            assertTrue((repository.read("default") as ShellReadResult.Ready).snapshot.collection.rows.isEmpty())
            File(root, "unrelated.Json").writeText("not this library")
            assertNull(withTimeoutOrNull(400) { events.receive() })
            val target = File(root, LibraryCatalog.DEFAULT_FILE)
            val temp = File(root, target.name + ".tmp")
            val card = CardInfo("Late scan", "", "", "", "2")
            ObjectOutputStream(temp.outputStream()).use {
                it.writeObject(Biblio(target.name, "Synthetic").apply { addCard(card) })
            }
            assertTrue(temp.renameTo(target))
            withTimeout(5000) { events.receive() }
            val bytes = target.readBytes()
            val read = repository.read("default") as ShellReadResult.Ready
            assertEquals(card.collectionItemId, read.snapshot.collection.rows.single().id)
            assertEquals(2, read.snapshot.collection.rows.single().quantity)
            assertArrayEquals(bytes, target.readBytes())
            assertFalse(File(root, target.name + ".bak").exists())
            val ocr = LegacyShellNavigation.intent(context, "default", ShellLegacyDestination.LIBRARY)!!
            assertEquals(OcrCaptureActivity::class.java.name, ocr.component!!.className)
            assertEquals("0", ocr.getStringExtra(App.INTENT_PERSISTOR_MODE))
            assertFalse(ocr.getBooleanExtra("askStartupPriceRefresh", true))
            val triple = LegacyShellNavigation.intent(context, "default", ShellLegacyDestination.TRIPLE)!!
            assertEquals(TripleScanActivity::class.java.name, triple.component!!.className)
            assertNull(LegacyShellNavigation.intent(context, "stale", ShellLegacyDestination.TRIPLE))
        } finally {
            observation.cancelAndJoin()
            root.deleteRecursively()
            listOf("library_catalog", "premium_access").forEach { base.deleteSharedPreferences(prefix + it) }
        }
    }
}
