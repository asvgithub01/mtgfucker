package io.asv.mtgocr.ocrreader

import android.graphics.*
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.ProgressBar
import androidx.test.core.app.ActivityScenario
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TriplePhotoLanguageTest {
    companion object {
        fun photo():ByteArray {
            val b=Bitmap.createBitmap(630,880,Bitmap.Config.ARGB_8888)
            val c=Canvas(b);c.drawColor(Color.WHITE)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;textSize=32f }
            c.drawText("Llanowar Elves",35f,85f,p)
            p.textSize=26f
            c.drawText("Add one mana of any color",35f,570f,p)
            c.drawText("to your mana pool.",35f,615f,p)
            return ByteArrayOutputStream().use { out -> b.compress(Bitmap.CompressFormat.JPEG,95,out);b.recycle();out.toByteArray() }
        }
    }
    @Test fun backgroundPhotoUsesItsOwnIdentityAndCompletesFailures() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val index=TitleLanguageIndex.read(ctx.assets.open(TitleLanguageIndex.ASSET))
        val reader=TriplePhotoLanguageReader({index},{})
        try {
            for((bytes,name,expected) in listOf(Triple(photo(),"Llanowar Elves","en"),
                    Triple(photo(),"Cyclopean Mummy","en"),Triple(byteArrayOf(1,2,3),"Llanowar Elves",null))) {
                val done=CountDownLatch(1);var result:String?="uncompleted"
                Handler(Looper.getMainLooper()).post { reader.submit(bytes,name) { result=it;done.countDown() } }
                assertTrue("Photo language did not complete",done.await(60,TimeUnit.SECONDS))
                assertEquals(expected,result)
            }
        } finally { Handler(Looper.getMainLooper()).post { reader.close() } }
    }
    @Test fun galleryShowsLoadingAndUpdatesOriginalRowAfterMovingOn() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=ctx.getSharedPreferences("triple_scanner",0);val old=prefs.all
        val before=ctx.filesDir.listFiles().orEmpty().filter { it.extension.equals("Json",true) }
            .associate { it.name to it.readBytes().toList() }
        prefs.edit().putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false).putString("locked_set","").commit()
        fun field(n:String)=TripleScanActivity::class.java.getDeclaredField(n).apply { isAccessible=true }
        var row:Any?=null
        fun rf(n:String)=row!!.javaClass.getDeclaredField(n).apply { isAccessible=true }
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(ctx,TripleScanActivity::class.java)
                .putExtra("triple_diagnostic_only",true)).use { scene ->
                scene.onActivity { a ->
                    field("fixedLanguage").set(a,null);field("allowedSets").set(a,emptySet<String>())
                    val offer=TripleScanActivity::class.java.getDeclaredMethod("offerVisual",TripleScanActivity.Target::class.java,
                        Long::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
                    val t=TripleScanActivity.Target("fixture","fixture","Llanowar Elves","4ED","Llanowar Elves","","nonfoil","EdScan",true)
                    assertTrue(offer.invoke(a,t,SystemClock.elapsedRealtime(),true) as Boolean)
                    val entries=field("galleryEntries").get(a) as Map<*,*>
                    row=entries[field("currentEncounter").getLong(a).toString()]!!
                    rf("photo").set(row,photo());field("ocrEnabled").setBoolean(a,true)
                    val schedule=TripleScanActivity::class.java.getDeclaredMethod("schedulePhotoLanguage",row!!.javaClass).apply { isAccessible=true }
                    schedule.invoke(a,row)
                    assertTrue(rf("languagePending").getBoolean(row))
                    assertEquals(View.VISIBLE,(rf("languageSpinner").get(row) as ProgressBar).visibility)
                    assertFalse((field("ocrScheduled").get(a) as java.util.concurrent.atomic.AtomicBoolean).get())
                    // Simulate moving to another encounter while this immutable row is still processing.
                    field("currentEncounter").setLong(a,999)
                }
                val deadline=SystemClock.elapsedRealtime()+60000;var pending=true
                while(pending && SystemClock.elapsedRealtime()<deadline) {
                    Thread.sleep(100)
                    scene.onActivity { pending=rf("languagePending").getBoolean(row) }
                }
                scene.onActivity { a ->
                    assertFalse(pending);assertEquals("en",rf("language").get(row))
                    assertEquals(View.GONE,(rf("languageSpinner").get(row) as ProgressBar).visibility)
                    assertEquals(999L,field("currentEncounter").getLong(a))
                    assertNull(field("observedLanguage").get(a))
                }
            }
            assertEquals(before,ctx.filesDir.listFiles().orEmpty().filter { it.extension.equals("Json",true) }.associate { it.name to it.readBytes().toList() })
        } finally {
            val edit=prefs.edit().clear()
            for((k,v) in old) when(v) {
                is Boolean -> edit.putBoolean(k,v)
                is String -> edit.putString(k,v)
                is Int -> edit.putInt(k,v)
                is Long -> edit.putLong(k,v)
                is Float -> edit.putFloat(k,v)
                is Set<*> -> edit.putStringSet(k,v.filterIsInstance<String>().toSet())
            }
            edit.commit()
        }
    }

}
