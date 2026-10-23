package io.asv.mtgocr.ocrreader.architecture

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.data.legacy.AndroidLegacyCollectionSource
import io.asv.mtgocr.ocrreader.data.legacy.LegacyCollectionReader
import io.asv.mtgocr.ocrreader.domain.collection.*
import io.asv.mtgocr.ocrreader.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ObjectOutputStream
import java.util.UUID

class CollectionReadIntegrationTest {
    @Test fun explicitReadPreservesPrimaryBackupPreferencesAndVariants() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "collection-source-test-" + UUID.randomUUID()
        val root = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        val prefNames = listOf("library_catalog", "premium_access")
        fun prefs() = prefNames.associateWith { context.getSharedPreferences(it, 0).all.toMap() }
        fun files() = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes().toList() }
        fun fixture(name: String, value: Any) {
            ObjectOutputStream(File(root, name).outputStream()).use { it.writeObject(value) }
        }
        try {
            context.getSharedPreferences("premium_access", 0).edit().putBoolean("premium_enabled", true).commit()
            context.getSharedPreferences("library_catalog", 0).edit()
                .putStringSet("libraries", setOf("extra\tExtra\tbiblio_extra.Json"))
                .putString("active_library_id", "default").commit()
            File(root, LibraryCatalog.DEFAULT_FILE).writeText("unrelated active library")
            val card = CardInfo("Localized", "2 EUR", "Text", "image", "3").apply {
                printingUuid = "printing"
                finish = "foil"
                languageCode = "pt"
                condition = "good"
                groups.add("group")
                decks.add("deck")
                sideboardDecks.add("sideboard")
                addScanEvidence("metadata", "scan_evidence/not-deleted.jpg")
            }
            val collection = Biblio("biblio_extra.Json", "Extra").apply { addCard(card) }
            fixture("biblio_extra.Json", collection)
            File(root, "biblio_extra.Json.bak").writeBytes(File(root, "biblio_extra.Json").readBytes())
            File(root, "scan_evidence/not-deleted.jpg").apply { parentFile!!.mkdirs(); writeText("synthetic image") }
            val reader = LegacyCollectionReader(AndroidLegacyCollectionSource(context), Dispatchers.IO)
            val beforeFiles = files()
            val beforePrefs = prefs()
            val primary = reader.read("extra") as CollectionReadResult.Ready
            val row = primary.snapshot.rows.single()
            assertEquals("extra", primary.snapshot.libraryId)
            assertEquals(card.collectionItemId, row.id)
            assertEquals("printing", row.printingUuid)
            assertEquals("foil", row.finish)
            assertEquals("pt", row.language)
            assertEquals("good", row.condition)
            assertEquals(3, row.quantity)
            assertEquals(listOf("group"), row.groups)
            assertEquals(listOf("deck"), row.decks)
            assertEquals(listOf("sideboard"), row.sideboardDecks)
            assertEquals("2 EUR", row.storedPrice)
            assertEquals(1, row.evidenceCount)
            assertEquals(beforeFiles, files())
            assertEquals(beforePrefs, prefs())
            File(root, "biblio_extra.Json").writeText("corrupt primary: do not repair")
            val corrupt = files()
            assertEquals(primary, reader.read("extra"))
            assertEquals(CollectionReadResult.Failed(CollectionReadFailure.UNKNOWN_LIBRARY), reader.read("unknown"))
            assertEquals(corrupt, files())
            assertEquals(beforePrefs, prefs())
        } finally {
            root.deleteRecursively()
            prefNames.forEach { base.deleteSharedPreferences(prefix + it) }
        }
    }
}
