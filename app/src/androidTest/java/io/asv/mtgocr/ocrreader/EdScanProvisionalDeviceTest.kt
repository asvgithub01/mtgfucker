package io.asv.mtgocr.ocrreader
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.UUID

class EdScanProvisionalDeviceTest {
    @Test fun provisionalConflictSurvivesWithoutPhotoAndCanBeAcknowledged() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val prefix="provisional-test-"+UUID.randomUUID()
        val directory=File(base.cacheDir,prefix).apply { mkdirs() }
        val prefs=mutableSetOf<String>()
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=directory
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                prefs.add(prefix+name);return base.getSharedPreferences(prefix+name,mode)
            }
        }
        try {
            val library=LibraryCatalog.activeFile(context)
            val card=CardInfo("Call for Unity","1 EUR","","","1").apply {
                printingUuid="printing";finish="nonfoil";languageCode="es";setCode="AER"
            }
            val collection=Biblio(library,"Test").apply { addCard(card) }
            ObjectOutputStream(FileOutputStream(File(directory,library))).use { it.writeObject(collection) }
            val key=UUID.randomUUID().toString()
            ScanReviewStore.writeProvisional(context,key,null,library,card.collectionItemId,listOf("aer","promo"))
            val entry=ScanReviewStore.read(context,library).single()
            assertTrue(entry.provisional);assertEquals("conflict",entry.state);assertTrue(entry.visual)
            assertFalse(ReviewApplyPolicy.applicable(entry)) // no arbitrary bulk correction
            ScanReviewStore.acknowledge(context,entry,card)
            assertEquals("reviewed",ScanReviewStore.read(context,library).single().state)
            assertEquals(1,collection.cards.single().quantityCount)
        } finally {
            directory.deleteRecursively()
            prefs.forEach { base.getSharedPreferences(it,0).edit().clear().commit() }
        }
    }
}
