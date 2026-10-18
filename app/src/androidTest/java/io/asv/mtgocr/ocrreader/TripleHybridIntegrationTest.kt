package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Real Triple activity/engine, emulator only; diagnostic flag prevents collection writes. */
@RunWith(AndroidJUnit4::class)
class TripleHybridIntegrationTest {
    @Test fun realTriplePreparesHybridAndPreservesScopeAndCollection() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("triple_scanner",0)
        val old=prefs.all
        fun hashes()=context.filesDir.listFiles().orEmpty().filter { it.extension.equals("Json",true) }
            .associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val before=hashes()
        fun field(name:String)=TripleScanActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
        prefs.edit().putBoolean("ar",false).putBoolean("art",true).putBoolean("ocr",true).putString("locked_set","").commit()
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(context,TripleScanActivity::class.java)
                .putExtra("triple_diagnostic_only",true)).use { scene ->
                var engine:NativeCollectorVisionEngine?=null;var index:FastRegionIndex?=null
                val deadline=SystemClock.elapsedRealtime()+120000
                while((engine==null || index==null) && SystemClock.elapsedRealtime()<deadline) {
                    scene.onActivity { a ->
                        engine=field("engine").get(a) as NativeCollectorVisionEngine?
                        index=field("hybridIndex").get(a) as FastRegionIndex?
                    }
                    if(engine==null || index==null) Thread.sleep(200)
                }
                assertNotNull(index);assertNotNull(engine)
                val cases=listOf(
                    "edfast-region-replay/1792148312327.jpg" to "cyclopean mummy",
                    "edfast-region-replay/1792157079658.jpg" to "bartolomé del presidio",
                    "edfast-cycle-replay/mountain.jpg" to "mountain")
                for((path,name) in cases) {
                    val file=File(context.cacheDir,path);assertTrue("Required private fixture $path",file.isFile)
                    val bitmap=BitmapFactory.decodeFile(file.path)
                    try {
                        val e=requireNotNull(engine);val idx=requireNotNull(index)
                        e.configureFast(idx,FastRegion.ART)
                        val scan=e.scan(bitmap)
                        assertTrue(scan.present);assertFalse(scan.regionConflict)
                        assertFalse(scan.regionDiagnostics.contains("fullTop=null"))
                        assertEquals(name,idx.byId.getValue(scan.hits.first().cardId).identity)
                        val confirmation=TripleIdentityConfirmation()
                        var result:TripleIdentityConfirmation.Result?=null
                        repeat(2) {
                            val actual=e.scan(bitmap)
                            result=confirmation.observe(actual.hits.map { h ->
                                val row=idx.byId.getValue(h.cardId)
                                EdScanFastIdentity.Candidate(h.cardId,h.score,row.identity,row.set,row.released)
                            },actual.comparisonRegion=="full",SystemClock.elapsedRealtime())
                        }
                        assertTrue("Triple must confirm identity like Fast: $name",result!!.confirmation.reliable)
                        if(name=="cyclopean mummy") assertFalse("Reprint ties remain pending",result!!.printingConfirmed)
                        // Scoped searches cannot leak global hits, even with both lanes active.
                        val scope=scan.hits.map { it.cardId }.take(2).toSet()
                        val scoped=e.scan(bitmap,scope)
                        assertTrue(scoped.hits.all { it.cardId in scope })
                        assertTrue(e.scan(bitmap,emptySet()).hits.isEmpty())
                    } finally { bitmap.recycle() }
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
