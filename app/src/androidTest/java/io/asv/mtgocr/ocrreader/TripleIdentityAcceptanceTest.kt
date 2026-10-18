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
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class TripleIdentityAcceptanceTest {
    @Test fun confirmedIdentityCanAddPendingEditionButWeakArAndStaleCannot() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("triple_scanner",0);val old=prefs.all
        fun hashes()=context.filesDir.listFiles().orEmpty().filter { it.extension.equals("Json",true) }
            .associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val before=hashes()
        prefs.edit().putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false).putString("locked_set","").commit()
        fun field(name:String)=TripleScanActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
        val visual=TripleScanActivity::class.java.getDeclaredMethod("offerVisual",TripleScanActivity.Target::class.java,
            Long::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
        val offer=TripleScanActivity::class.java.getDeclaredMethod("offer",TripleScanActivity.Target::class.java,
            Long::class.javaPrimitiveType,String::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
        fun target(id:String)=TripleScanActivity.Target(id,id,id,"4ED",id,"","nonfoil","EdScan",true)
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(context,TripleScanActivity::class.java)
                .putExtra("triple_diagnostic_only",true)).use { scene ->
                scene.onActivity { a ->
                    field("fixedLanguage").set(a,null);field("allowedSets").set(a,emptySet<String>())
                    field("artEnabled").setBoolean(a,true) // no engine start, but retain mixed-AR safeguards
                    val entries=field("galleryEntries").get(a) as Map<*,*>
                    val base=entries.size
                    val t=target("cyclopean mummy")
                    assertFalse(visual.invoke(a,t,SystemClock.elapsedRealtime(),false) as Boolean)
                    assertTrue(visual.invoke(a,t,SystemClock.elapsedRealtime(),true) as Boolean)
                    assertEquals(base+1,entries.size)
                    val row=entries[field("currentEncounter").getLong(a).toString()]!!
                    fun value(name:String)=row.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(row)
                    assertTrue((value("target") as TripleScanActivity.Target).provisional)
                    assertNull(value("language"))
                    assertFalse(visual.invoke(a,t,SystemClock.elapsedRealtime(),true) as Boolean)
                    assertEquals(base+1,entries.size)
                    val button=a.window.decorView.findViewWithTag<android.widget.Button>("triple_rescan")
                    assertEquals(android.view.View.VISIBLE,button.visibility)
                    button.performClick()
                    assertEquals(android.view.View.GONE,button.visibility)
                    assertFalse(visual.invoke(a,target("wrong"),SystemClock.elapsedRealtime(),false) as Boolean)
                    assertFalse(visual.invoke(a,target("wrong"),SystemClock.elapsedRealtime()-3000,true) as Boolean)
                    val ar=target("wrong").copy(source="AR",provisional=false)
                    assertFalse(offer.invoke(a,ar,SystemClock.elapsedRealtime(),"AR",false) as Boolean)
                    field("allowedSets").set(a,setOf("ICE"))
                    assertFalse(visual.invoke(a,target("wrong"),SystemClock.elapsedRealtime(),true) as Boolean)
                    assertEquals(base+1,entries.size)
                    field("allowedSets").set(a,emptySet<String>())
                    // Simulated fresh confirmed image, strictly after the tap boundary.
                    Thread.sleep(2)
                    val fresh=SystemClock.elapsedRealtime()
                    assertTrue(visual.invoke(a,t,fresh,true) as Boolean)
                    assertEquals(base+2,entries.size)
                    val next=entries[field("currentEncounter").getLong(a).toString()]!!
                    fun rowField(r:Any,n:String)=r.javaClass.getDeclaredField(n).apply { isAccessible=true }.get(r)
                    assertNotSame(row,next)
                    assertEquals(1,rowField(row,"count"));assertEquals(1,rowField(next,"count"))
                    assertNotEquals(rowField(row,"photoKey"),rowField(next,"photoKey"))
                    assertFalse(visual.invoke(a,t,SystemClock.elapsedRealtime(),true) as Boolean)
                    assertEquals(base+2,entries.size)
                    // Different identities keep scanning without touching the repeat button.
                    Thread.sleep(650)
                    assertTrue(visual.invoke(a,target("frozen shade"),SystemClock.elapsedRealtime(),true) as Boolean)
                    assertEquals(base+3,entries.size)
                    assertFalse(visual.invoke(a,target("frozen shade"),SystemClock.elapsedRealtime(),true) as Boolean)
                    Thread.sleep(650)
                    assertTrue(visual.invoke(a,t,SystemClock.elapsedRealtime(),true) as Boolean) // A -> B -> A
                    assertEquals(base+4,entries.size)
                    assertFalse(visual.invoke(a,t,SystemClock.elapsedRealtime(),true) as Boolean)
                }
            }
            assertEquals(before,hashes())
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
