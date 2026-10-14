package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Inject recorded failure patterns into the real Activity; never persist inventory. */
@RunWith(AndroidJUnit4::class)
class TripleArbitrationIntegrationTest {
    @Test fun successiveVisualCardsKeepRowsAndFixedScopeWithoutHumanCameraInput() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val files=LibraryCatalog.libraries(context).map { File(context.filesDir,it.fileName) }.filter { it.isFile }
        fun hashes()=files.associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val before=hashes();val prefs=context.getSharedPreferences("triple_scanner",0)
        val modes=listOf("ar","art","ocr").associateWith { prefs.getBoolean(it,true) }
        val installed=requireNotNull(ArPackageStore.installed(context,"FRA"))
        fun target(number:String,source:String):TripleScanActivity.Target {
            val r=installed.references.first { it.label.endsWith(" · FRA #$number") }
            return TripleScanActivity.Target(r.scryfallId,r.printing,r.label.substringBefore(" · "),"FRA",r.label,r.image,r.finish,source)
        }
        val loyal=target("424","AR");val danitha=target("196","EdScan")
        fun field(name:String)=TripleScanActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
        val offer=TripleScanActivity::class.java.getDeclaredMethod("offer",TripleScanActivity.Target::class.java,
            Long::class.javaPrimitiveType,String::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
        fun read(a:TripleScanActivity,t:TripleScanActivity.Target):Boolean =
            offer.invoke(a,t,SystemClock.elapsedRealtime(),t.source,false) as Boolean
        prefs.edit().putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false).commit()
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(context,TripleScanActivity::class.java).putExtra("triple_diagnostic_only",true)).use { scene ->
                scene.onActivity { a ->
                    field("fixedLanguage").set(a,"en");field("allowedSets").set(a,setOf("FRA"))
                    assertFalse(read(a,loyal))
                }
                Thread.sleep(700)
                scene.onActivity { a ->
                    assertTrue(read(a,loyal))
                    assertFalse(field("confirmationPending").getBoolean(a))
                    field("confirmationPending").setBoolean(a,true) // a weak variant audit had asked to retry
                    assertTrue(read(a,loyal))
                    assertFalse(field("confirmationPending").getBoolean(a))
                }
                Thread.sleep(700)
                scene.onActivity { a ->
                    assertTrue(read(a,danitha)) // previous AR identity must neither lock nor reuse row
                    val entries=field("galleryEntries").get(a) as Map<*,*>
                    assertEquals(2,entries.size)
                    entries.values.forEach { row ->
                        val type=row!!.javaClass
                        assertEquals("en",type.getDeclaredField("language").apply { isAccessible=true }.get(row))
                        assertTrue(type.getDeclaredField("manualLanguage").apply { isAccessible=true }.getBoolean(row))
                    }
                    for(source in listOf("AR","EdScan","OCR"))
                        assertFalse(read(a,loyal.copy(set="SPG",source=source)))
                    assertEquals(2,entries.size)
                    // Clear a disabled motor's votes, but retain current-card deduplication.
                    val arbiter=field("arbiter").get(a) as TripleRecognitionArbiter
                    arbiter.resetPending()
                    assertTrue(read(a,danitha.copy(source="AR")))
                    assertEquals(2,entries.size)
                    // An OCR miss/unknown edition does not block the next already-known visual card.
                    read(a,loyal.copy(id="ocr-name:loyaltutor",printing="",set="",source="OCR",provisional=true))
                }
                Thread.sleep(700)
                scene.onActivity { a ->
                    assertTrue(read(a,loyal.copy(source="EdScan")))
                    assertEquals(3,(field("galleryEntries").get(a) as Map<*,*>).size)
                }
                Thread.sleep(700)
                scene.onActivity { a -> assertFalse(read(a,danitha.copy(source="OCR"))) }
                Thread.sleep(700)
                scene.onActivity { a ->
                    assertTrue(read(a,danitha.copy(source="OCR")))
                    val entries=field("galleryEntries").get(a) as Map<*,*>
                    assertEquals(4,entries.size)
                    entries.values.forEach { row -> assertEquals("en",row!!.javaClass.getDeclaredField("language").apply { isAccessible=true }.get(row)) }
                }
            }
        } finally {
            val editor=prefs.edit();modes.forEach { (key,value)->editor.putBoolean(key,value) };editor.commit()
            check(hashes()==before) { "Arbitration replay must not modify any library" }
        }
    }
}
