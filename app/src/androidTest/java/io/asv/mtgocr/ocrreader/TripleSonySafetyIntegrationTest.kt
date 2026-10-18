package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Real Activity, synthetic captures, diagnostic-only. Never run against a user's phone. */
@RunWith(AndroidJUnit4::class)
class TripleSonySafetyIntegrationTest {
    @Test fun mixedArCannotPersistPhantomsAndKnownLanguagesKeepSeparateRowsAndPhotos() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Emulator only" }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("triple_scanner",0)
        val old=prefs.all
        fun hashes()=context.filesDir.listFiles().orEmpty().filter { it.extension.equals("Json",true) }
            .associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val before=hashes()
        prefs.edit().putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false)
            .remove("scope_language").remove("language_scope_sets").putString("locked_set","").commit()
        fun field(name:String)=TripleScanActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
        fun rowValue(row:Any,name:String)=row.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(row)
        fun rowSet(row:Any,name:String,value:Any?) { row.javaClass.getDeclaredField(name).apply { isAccessible=true }.set(row,value) }
        fun entries(a:TripleScanActivity)=field("galleryEntries").get(a) as Map<*,*>
        val offer=TripleScanActivity::class.java.getDeclaredMethod("offer",TripleScanActivity.Target::class.java,
            Long::class.javaPrimitiveType,String::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
        val language=TripleScanActivity::class.java.getDeclaredMethod("applyPhysicalLanguage",String::class.java,String::class.java,
            String::class.java,Long::class.javaPrimitiveType,Int::class.javaPrimitiveType,Long::class.javaPrimitiveType,ByteArray::class.java).apply { isAccessible=true }
        fun target(name:String,source:String)=TripleScanActivity.Target(name,"",name,"",name,"","nonfoil",source)
        fun tap(a:TripleScanActivity) {
            a.window.decorView.findViewWithTag<android.widget.Button>("triple_rescan").performClick()
            Thread.sleep(2)
        }
        fun read(a:TripleScanActivity,name:String,source:String)=offer.invoke(a,target(name,source),SystemClock.elapsedRealtime(),source,false) as Boolean
        fun lang(a:TripleScanActivity,code:String,event:Long=field("currentEncounter").getLong(a),photo:ByteArray=byteArrayOf(2)) {
            language.invoke(a,"will o the wisp",code,"title",event,field("generation").getInt(a),SystemClock.elapsedRealtime(),photo)
        }
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(context,TripleScanActivity::class.java).putExtra("triple_diagnostic_only",true)).use { scene ->
                scene.onActivity { a ->
                    field("allowedSets").set(a,emptySet<String>());field("fixedLanguage").set(a,null)
                    field("artEnabled").setBoolean(a,true);field("ocrEnabled").setBoolean(a,true)
                    assertTrue(read(a,"northern paladin","EdScan"))
                    assertFalse(read(a,"cloak of confusion","AR"))
                }
                Thread.sleep(700)
                scene.onActivity { a ->
                    assertFalse(read(a,"cloak of confusion","AR"))
                    assertEquals(1,entries(a).size)
                    tap(a)
                    assertTrue(read(a,"cuombajj witches","EdScan"))
                    assertEquals(2,entries(a).size)
                }
                Thread.sleep(700)
                val english=byteArrayOf(1);val italian=byteArrayOf(2)
                var oldEvent=0L;var oldRow:Any?=null;var oldPhotoKey:Any?=null
                scene.onActivity { a ->
                    tap(a)
                    assertTrue(read(a,"will o the wisp","EdScan"))
                    oldEvent=field("currentEncounter").getLong(a);oldRow=entries(a)[oldEvent.toString()]
                    rowSet(oldRow!!,"photo",english);rowSet(oldRow!!,"count",3)
                    rowSet(oldRow!!,"selectedFinish","foil");rowSet(oldRow!!,"manualFinish",true)
                    rowSet(oldRow!!,"manualEdition",true);oldPhotoKey=rowValue(oldRow!!,"photoKey")
                    lang(a,"en",photo=english) // previously unknown, completes SAME row
                    assertEquals("en",rowValue(oldRow!!,"language"));assertEquals(3,entries(a).size)
                }
                Thread.sleep(700)
                scene.onActivity { a -> lang(a,"it",photo=italian);assertEquals(3,entries(a).size) }
                Thread.sleep(700)
                scene.onActivity { a ->
                    lang(a,"it",photo=italian)
                    assertEquals(3,entries(a).size) // pending language cannot create an unsolicited copy
                    tap(a)
                    assertTrue(read(a,"will o the wisp","EdScan"))
                    val freshRow=entries(a)[field("currentEncounter").getLong(a).toString()]!!
                    rowSet(freshRow,"photo",italian)
                    lang(a,"it",photo=italian)
                    assertEquals(4,entries(a).size)
                    assertEquals("en",rowValue(oldRow!!,"language"));assertEquals(3,rowValue(oldRow!!,"count"))
                    assertEquals(oldPhotoKey,rowValue(oldRow!!,"photoKey"));assertSame(english,rowValue(oldRow!!,"photo"))
                    val newRow=entries(a)[field("currentEncounter").getLong(a).toString()]!!
                    assertEquals("it",rowValue(newRow,"language"));assertEquals(1,rowValue(newRow,"count"))
                    assertEquals("nonfoil",rowValue(newRow,"selectedFinish"));assertSame(italian,rowValue(newRow,"photo"))
                    assertNotEquals(oldPhotoKey,rowValue(newRow,"photoKey"));assertEquals(false,rowValue(newRow,"manualEdition"))
                    assertFalse(rowValue(newRow,"manualEdition") as Boolean)
                    lang(a,"pt",event=oldEvent) // late callback cannot rewrite the previous row
                    assertEquals("en",rowValue(oldRow!!,"language"));assertEquals(4,entries(a).size)
                    rowSet(newRow,"manualLanguage",true);rowSet(newRow,"language","en")
                    lang(a,"pt") // user's fixed language wins, discrepancy is recorded
                    assertEquals("en",rowValue(newRow,"language"));assertEquals("pt",rowValue(newRow,"physicalLanguage"))
                    assertEquals(true,rowValue(newRow,"languageMismatch"));assertEquals(4,entries(a).size)
                }
            }
        } finally {
            val edit=prefs.edit().clear()
            old.forEach { (k,v) -> when(v) {
                is Boolean->edit.putBoolean(k,v);is String->edit.putString(k,v);is Int->edit.putInt(k,v)
                is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v)
                is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())
            } };edit.commit()
            assertEquals("Diagnostic replay changed library files",before,hashes())
        }
    }
}
