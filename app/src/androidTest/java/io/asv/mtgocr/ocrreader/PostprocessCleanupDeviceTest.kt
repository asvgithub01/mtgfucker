package io.asv.mtgocr.ocrreader

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ObjectOutputStream
import java.util.UUID

class PostprocessCleanupDeviceTest {
    @Test fun cleanupPreservesInventoryPendingSharedAndForeignEvidence() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(app.cacheDir,"cleanup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(app) {
            override fun getFilesDir()=root
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("cleanup-isolated-$name",mode)
        }
        val library=LibraryCatalog.activeFile(context)
        val card=CardInfo("Test", "", "", "", "7").apply { finish="foil";languageCode="en" }
        val biblio=Biblio(library,"Test").apply { cards.add(card) }
        val collection=File(root,library)
        ObjectOutputStream(collection.outputStream()).use { it.writeObject(biblio) }
        val before=collection.readBytes()
        val dir=File(root,"edscan").apply { mkdirs() }
        fun photo(owner:String=card.collectionItemId,lib:String=library,reviewed:Boolean=false):String {
            val key=UUID.randomUUID().toString()
            File(dir,"$key.link.json").writeText(JSONObject().put("library",lib).put("collectionItemId",owner).toString())
            File(dir,"$key.json").writeText("{\"source\":\"edscan\"}")
            File(dir,"$key.jpg").writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("triple-card.jpg").use { it.readBytes() })
            if(reviewed) File(dir,"$key.review.json").writeText(JSONObject().put("stamp",0).put("printingUuid",card.printingUuid.orEmpty())
                .put("finish",card.finish).put("language",card.languageCode).toString())
            return key
        }
        try {
            val done=photo(reviewed=true)
            val pending=photo()
            val deleted=photo(owner="deleted-item")
            val foreign=photo(owner="deleted-item",lib="other-library")
            val shared=photo(reviewed=true)
            val marker=UUID.randomUUID().toString()
            File(dir,"$marker.provisional.json").writeText(JSONObject().put("library",library)
                .put("collectionItemId",card.collectionItemId).put("photoKey",shared).toString())
            assertTrue(PostprocessCleanup.clean(context,library)>0)
            assertFalse(File(dir,"$done.jpg").exists())
            assertFalse(File(dir,"$deleted.jpg").exists())
            for(key in listOf(pending,foreign,shared)) assertTrue(File(dir,"$key.jpg").exists())
            assertArrayEquals(before,collection.readBytes())
            assertEquals(0,PostprocessCleanup.clean(context,library))
            ObjectOutputStream(File(root,"$library.bak").outputStream()).use { it.writeObject(Biblio(library,"Old empty backup")) }
            collection.writeText("corrupt primary")
            assertEquals(0,PostprocessCleanup.clean(context,library))
            assertTrue(File(dir,"$pending.jpg").exists())
            collection.delete()
            assertEquals(0,PostprocessCleanup.clean(context,library))
            assertTrue(File(dir,"$pending.jpg").exists())
        } finally { root.deleteRecursively() }
    }
}
