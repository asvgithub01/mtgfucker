package io.asv.mtgocr.ocrreader

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.*
import io.asv.mtgocr.ocrreader.model.Biblio
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EdScanFastPersistenceTest {
    @Test fun newRowsStayProvisionalUnknownAndRetriesKeepSameOwnedId() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Emulator only" }
        val test=InstrumentationRegistry.getInstrumentation();val real=test.targetContext
        val before=LegacyCollectionStore.cards(real).map { it.collectionItemId to it.quantity }
        val original=OcrCaptureActivity.mBiblio
        val folder=File(real.cacheDir,"edfast-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(real) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=folder
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(folder.name+name,mode)
        }
        CardRepository.get(real)
        val owned=mutableSetOf<String>()
        try {
            DataUtils.saveSerializable(context,Biblio(LibraryCatalog.DEFAULT_FILE,"Test"),LibraryCatalog.DEFAULT_FILE)
            val ref=JSONObject(test.context.assets.open("triple-card.json").bufferedReader().use { it.readText() }).getJSONArray("references").getJSONObject(0)
            val target=TripleScanActivity.Target(ref.getString("scryfallId"),ref.getString("printing"),ref.getString("label").substringBefore(" · "),
                ref.getString("setCode"),ref.getString("label"),ref.getString("image"),ref.getString("finish"),"EdScan",true)
            val session=TripleCollectionSession(context,{},"edfast","EdScan Fast")
            val state=TripleCollectionSession.State(1,target,null,target.finish,1,
                test.context.assets.open("triple-card.jpg").use { it.readBytes() },false,java.util.UUID.randomUUID().toString())
            fun submit(s:TripleCollectionSession.State):io.asv.mtgocr.ocrreader.model.CardInfo {
                val latch=CountDownLatch(1);var error:Throwable?=null;var result:io.asv.mtgocr.ocrreader.model.CardInfo?=null
                test.runOnMainSync { session.submit(s) { card,_,failure -> result=card;error=failure;card?.let { owned+=it.collectionItemId };latch.countDown() } }
                assertTrue(latch.await(90,TimeUnit.SECONDS));assertNull(error);return requireNotNull(result)
            }
            val first=submit(state)
            assertEquals("",first.languageCode);assertTrue(first.isProvisionalEdition)
            assertTrue(first.groups.any { it.startsWith("EdScan Fast ") })
            assertEquals(first.collectionItemId,submit(state).collectionItemId)
            val next=submit(state.copy(event=2,photoKey=java.util.UUID.randomUUID().toString()))
            assertNotEquals(first.collectionItemId,next.collectionItemId)
            assertEquals(2,LegacyCollectionStore.cards(context).size)
            val meta=JSONObject(File(folder,"edscan/${state.photoKey}.json").readText())
            assertEquals("edfast",meta.getString("scanner"));assertEquals("ocr",meta.getString("source"))
            assertEquals("pending",meta.getString("languageStatus"));assertTrue(meta.getBoolean("provisional"))
            assertEquals(before,LegacyCollectionStore.cards(real).map { it.collectionItemId to it.quantity })
        } finally {
            owned.forEach { CardDatabase.get(real).cardDao().deleteOwnedPrinting(it) }
            OcrCaptureActivity.mBiblio=original
            check(folder.canonicalFile.parentFile==real.cacheDir.canonicalFile);folder.deleteRecursively()
        }
    }
}
