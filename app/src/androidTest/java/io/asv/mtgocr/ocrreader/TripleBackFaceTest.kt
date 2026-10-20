package io.asv.mtgocr.ocrreader

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.*
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Emulator only: isolated collection and synthetic catalog rows, never a user's Sony. */
class TripleBackFaceTest {
    private val test=InstrumentationRegistry.getInstrumentation()
    private val real get()=test.targetContext
    private fun emulator()=check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
    private fun lookup(service:TripleCardLookup,id:String):TripleCardLookup.Result? {
        val done=CountDownLatch(1);var result:TripleCardLookup.Result?=null
        test.runOnMainSync { assertTrue(service.lookup(id) { result=it;done.countDown() }) }
        assertTrue("Lookup must complete, not remain busy for _back",done.await(10,TimeUnit.SECONDS))
        return result
    }
    @Test fun networkAndDiskResolveBaseIdButKeepFaceImagesSeparate() {
        emulator()
        val id=java.util.UUID.randomUUID().toString()
        val folder=File(real.cacheDir,"back-lookup-$id").apply { mkdirs() }
        val context=object:ContextWrapper(real) {
            override fun getApplicationContext():Context=this
            override fun getCacheDir()=folder
        }
        val json=JSONObject().put("id",id).put("name","Throne of the Grim Captain // The Grim Captain")
            .put("set","lci").put("collector_number","313").put("rarity","rare")
            .put("finishes",org.json.JSONArray(listOf("nonfoil","foil")))
            .put("card_faces",org.json.JSONArray().put(JSONObject().put("image_uris",JSONObject().put("normal","front-image")))
                .put(JSONObject().put("image_uris",JSONObject().put("normal","back-image"))))
        var calls=0
        val service=TripleCardLookup(context)
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            assertEquals("https://api.scryfall.com/cards/$id",chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        TripleCardLookup::class.java.getDeclaredField("client").apply { isAccessible=true }.set(service,client)
        try {
            val back=lookup(service,id+"_back")!!
            assertEquals(id,back.printing);assertEquals("back-image",back.image);assertEquals("313",back.number)
            val front=lookup(service,id)!! // same metadata on disk, different face result
            assertEquals("front-image",front.image);assertEquals(back.printing,front.printing)
            assertEquals(back.name,front.name);assertEquals(1,calls)
            assertEquals(back,lookup(service,id+"_back")) // face-specific memory cache
            assertNull(lookup(service,id+"_back_back"));assertEquals(1,calls)
        } finally { test.runOnMainSync { service.close() };folder.deleteRecursively() }
    }
    @Test fun localBackPersistsSamePrintingAndRowWithManualFoil() {
        emulator()
        val id=java.util.UUID.randomUUID().toString();val uuid="back-fixture-$id"
        val folder=File(real.cacheDir,"back-save-$id").apply { mkdirs() }
        val context=object:ContextWrapper(real) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=folder
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("back-test-$id-$name",mode)
        }
        val db=CardDatabase.get(real);val dao=db.cardDao()
        val image="https://cards.scryfall.io/normal/front/4/f/$id.jpg"
        dao.savePrintings(listOf(CardPrintingEntity(uuid,"throne of the grim captain // the grim captain",
            "Throne of the Grim Captain // The Grim Captain","LCI","The Lost Caverns of Ixalan","313","2023-11-17",
            "rare",id,null,null,null,null,null,"nonfoil,foil","Artifact","",image,1,"a")))
        CardRepository.get(real)
        val original=OcrCaptureActivity.mBiblio
        val before=LegacyCollectionStore.cards(real).map { it.collectionItemId to it.quantity }
        val owned=mutableSetOf<String>();val service=TripleCardLookup(real)
        try {
            DataUtils.saveSerializable(context,Biblio(LibraryCatalog.DEFAULT_FILE,"Test"),LibraryCatalog.DEFAULT_FILE)
            val back=lookup(service,id+"_back")!!;val front=lookup(service,id)!!
            assertEquals(uuid,back.printing);assertEquals(front.printing,back.printing)
            assertEquals(image.replace("/front/","/back/"),back.image)
            val session=TripleCollectionSession(context) {}
            val target=TripleScanActivity.Target(id+"_back",back.printing,back.name,back.set,back.name+" #313",back.image,"nonfoil","EdScan")
            val state=TripleCollectionSession.State(1,target,null,"foil",1,byteArrayOf(1,2,3),true,id)
            fun submit(s:TripleCollectionSession.State):CardInfo {
                val done=CountDownLatch(1);var result:CardInfo?=null;var error:Throwable?=null
                test.runOnMainSync { session.submit(s) { card,_,failure ->
                    result=card;error=failure;card?.let { owned+=it.collectionItemId };done.countDown()
                } }
                assertTrue("Back save callback",done.await(30,TimeUnit.SECONDS));assertNull(error)
                return requireNotNull(result)
            }
            val first=submit(state)
            assertEquals(uuid,first.printingUuid);assertEquals("foil",first.finish)
            val flipped=submit(state.copy(target=target.copy(id=id,image=front.image),count=3))
            assertEquals(first.collectionItemId,flipped.collectionItemId)
            val backAgain=submit(state.copy(count=3))
            assertEquals(first.collectionItemId,backAgain.collectionItemId);assertEquals(3,backAgain.quantityCount)
            assertEquals("foil",backAgain.finish);assertEquals(1,LegacyCollectionStore.cards(context).size)
            val meta=JSONObject(File(folder,"edscan/$id.json").readText())
            assertEquals(id,meta.getString("id"));assertEquals(id+"_back",meta.getString("visualReference"))
            assertEquals(before,LegacyCollectionStore.cards(real).map { it.collectionItemId to it.quantity })
        } finally {
            test.runOnMainSync { service.close() }
            owned.forEach { dao.deleteOwnedPrinting(it) }
            db.openHelper.writableDatabase.execSQL("DELETE FROM card_printings WHERE uuid = ?",arrayOf(uuid))
            OcrCaptureActivity.mBiblio=original;folder.deleteRecursively()
        }
    }
}
