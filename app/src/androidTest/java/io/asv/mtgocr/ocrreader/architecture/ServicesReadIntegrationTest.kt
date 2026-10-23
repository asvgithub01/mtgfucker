package io.asv.mtgocr.ocrreader.architecture

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.legacy.AndroidServicesReadSource
import io.asv.mtgocr.ocrreader.data.legacy.LegacyServicesReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ServicesReadIntegrationTest {
    @Test fun canonicalMetadataReaderRejectsEscapingSymlinksWithoutChangingFiles() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(base.cacheDir, "metadata-test-" + UUID.randomUUID()).apply { mkdirs() }
        val root = File(fixture, "private").apply { mkdirs() }
        val outside = File(fixture, "outside").apply { writeText("untouched outside") }
        val inside = File(root, "library").apply { writeText("123") }
        val link = File(root, "escape")
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = root
        }
        try {
            android.system.Os.symlink(outside.absolutePath, link.absolutePath)
            val source = AndroidServicesReadSource(context)
            assertEquals(3L, source.bytes("library"))
            for (name in listOf("escape", "../outside")) {
                try { source.bytes(name); fail("must reject escaping path") }
                catch (error: IllegalArgumentException) {
                    assertEquals("Library file outside private root", error.message)
                }
            }
            assertEquals("untouched outside", outside.readText())
            assertEquals("123", inside.readText())
            assertEquals(outside.absolutePath, android.system.Os.readlink(link.absolutePath))
        } finally {
            // Unlink explicitly before recursively cleaning this isolated fixture.
            link.delete()
            fixture.deleteRecursively()
        }
    }

    @Test fun settingsAndAllLibraryFilesRemainUnchangedEvenWithoutPremium() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "services-test-" + UUID.randomUUID()
        val root = File(base.cacheDir, prefix).apply { mkdirs() }
        val names = listOf("library_catalog", "premium_access", "cloud_library_sync", "price_currency_preferences")
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        fun prefs(name: String) = context.getSharedPreferences(name, 0)
        fun files() = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes().toList() }
        fun settings() = names.associateWith { prefs(it).all.toMap() }
        try {
            prefs("library_catalog").edit().putStringSet("libraries", setOf("extra\tHidden library\tbiblio_extra.Json"))
                .putString("active_library_id", "extra").putString("pinned_background_extra", "card-id").commit()
            prefs("cloud_library_sync").edit().putBoolean("auto_sync_enabled", false)
                .putString("snapshot_private", "untouched").commit()
            prefs("price_currency_preferences").edit().putString("display_currency", "USD").commit()
            File(root, "myBiblio.Json").writeText("corrupt-but-must-not-repair")
            File(root, "biblio_extra.Json.bak").writeText("backup bytes")
            val beforeFiles = files(); val beforeSettings = settings()
            val reader = LegacyServicesReader(context)
            val result = reader.read()
            assertFalse(result.premium); assertFalse(result.autoSyncPreference); assertEquals("USD", result.currency)
            assertEquals(listOf("default", "extra"), result.libraries.map { it.id })
            assertFalse(result.libraries.last().available)
            assertNull(result.libraries.last().primaryBytes)
            assertEquals(12L, result.libraries.last().backupBytes)
            assertEquals(beforeFiles, files()); assertEquals(beforeSettings, settings())
            prefs("premium_access").edit().putBoolean("premium_enabled", true).commit()
            prefs("price_currency_preferences").edit().putString("display_currency", "unknown").commit()
            val changedSettings = settings()
            val premium = reader.read()
            assertTrue(premium.libraries.all { it.available }); assertEquals("EUR", premium.currency)
            assertEquals(changedSettings, settings()); assertEquals(beforeFiles, files())
        } finally {
            root.deleteRecursively()
            names.forEach { base.deleteSharedPreferences(prefix + it) }
        }
    }
}
