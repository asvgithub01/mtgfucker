package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArPackageSmokeTest {
    @Test fun generatesMissingSetLocallyAndCancellationKeepsInstalledVersion() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(PremiumAccess.isEnabled(context))
        val before=io.asv.mtgocr.ocrreader.data.LegacyCollectionStore.cards(context).map { it.collectionItemId }.sorted()
        ArLocalPackageBuilder.generate(context,ArLocalPackageBuilder.Edition("PAPC","Apocalypse Promos"),{}, { _,_-> })
        val installed=requireNotNull(ArPackageStore.installed(context,"PAPC"))
        assertEquals(1,installed.references.size)
        val session=com.google.ar.core.Session(context)
        try { assertEquals(1,installed.database.inputStream().use { com.google.ar.core.AugmentedImageDatabase.deserialize(session,it).numImages }) }
        finally { session.close() }
        try {
            ArLocalPackageBuilder.generate(context,ArLocalPackageBuilder.Edition("PAPC","Apocalypse Promos"),{ throw java.util.concurrent.CancellationException() },{ _,_-> })
            fail("Cancellation ignored")
        } catch(expected:java.util.concurrent.CancellationException) { }
        assertEquals(installed.entry.version,ArPackageStore.installedVersion(context,"PAPC"))
        assertEquals(before,io.asv.mtgocr.ocrreader.data.LegacyCollectionStore.cards(context).map { it.collectionItemId }.sorted())
    }
    @Test fun installsChecksumsAndLoadsPrecompiledDatabaseWithoutInference() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        assumeTrue(PremiumAccess.isEnabled(context))
        assumeTrue(!(context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
        val bytes=instrumentation.context.assets.open("ar/catalog.json").use { it.readBytes() }
        val entries=JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("packages")
        val e=(0 until entries.length()).map { ArPackageStore.parseEntry(entries.getJSONObject(it)) }.first { it.id=="FRA" }
        val before=io.asv.mtgocr.ocrreader.data.LegacyCollectionStore.cards(context).map { it.collectionItemId }.sorted()
        ArPackageStore.install(context,e,{ a,report -> instrumentation.context.assets.open("ar/"+a.file).use { it.readBytes() }.also { report(it.size.toLong()) } }) { _,_-> }
        assertEquals(e.version,ArPackageStore.installedVersion(context,e.id))
        try {
            ArPackageStore.install(context,e.copy(database=e.database.copy(sha256="0".repeat(64))),{ _,_-> byteArrayOf(1) }) { _,_-> }
            fail("Corrupt download accepted")
        } catch(expected:IllegalArgumentException) { }
        assertEquals(e.version,ArPackageStore.installedVersion(context,e.id))
        assertEquals(e.count,ArPackageStore.installed(context,e.id)!!.references.size)
        // Allow offline manager UI to use the very same validated catalog.
        File(context.filesDir,"ar-packages/catalog.json").writeBytes(bytes)
        ActivityScenario.launch<EdScannerArActivity>(Intent(context,EdScannerArActivity::class.java)
            .putExtra("edscan_test_no_save",true).putExtra("edscan_preload_last",true).putExtra("ar_package_id","FRA")).use { scenario ->
            val deadline=SystemClock.elapsedRealtime()+30000;var ready=0
            while(ready==0 && SystemClock.elapsedRealtime()<deadline) {
                scenario.onActivity { activity ->
                    val preview=EdScannerArActivity::class.java.getDeclaredField("arPreview").apply { isAccessible=true }.get(activity)
                    ready=EdScannerArPreview::class.java.getDeclaredField("batchReadyCount").apply { isAccessible=true }.getInt(preview)
                    assertEquals(0,io.asv.collectorvision.NativeCollectorVisionActivity::class.java.getDeclaredField("externalAnalyzedFrames").apply { isAccessible=true }.getInt(activity))
                };if(ready==0) Thread.sleep(100)
            }
            assertEquals(e.count,ready)
        }
        ActivityScenario.launch<ArDownloadsActivity>(Intent(context,ArDownloadsActivity::class.java)).use { scenario ->
            val deadline=SystemClock.elapsedRealtime()+60000
            var loaded=false
            while(!loaded && SystemClock.elapsedRealtime()<deadline) {
                scenario.onActivity { activity ->
                    loaded=!(ArDownloadsActivity::class.java.getDeclaredField("busy").apply { isAccessible=true }.getBoolean(activity))
                };if(!loaded)Thread.sleep(100)
            }
            assertTrue(loaded)
            scenario.onActivity { activity ->
                val search=ArDownloadsActivity::class.java.getDeclaredField("search").apply { isAccessible=true }.get(activity) as android.widget.EditText
                val list=ArDownloadsActivity::class.java.getDeclaredField("list").apply { isAccessible=true }.get(activity) as android.widget.ListView
                val selected=ArDownloadsActivity::class.java.getDeclaredField("selected").apply { isAccessible=true }.get(activity) as Set<*>
                search.setText("FRA");assertTrue(list.adapter.count>=1)
                val root=activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                root.findViewWithTag<android.widget.Button>("ar_select_all").performClick();assertTrue("FRA" in selected);assertEquals(list.adapter.count,selected.size)
                search.setText("");root.findViewWithTag<android.widget.Button>("ar_select_all").performClick();assertEquals(list.adapter.count,selected.size);assertTrue(selected.size>=2)
                root.findViewWithTag<android.widget.Button>("ar_clear_selection").performClick();assertTrue(selected.isEmpty())
            }
        }
        assertEquals(before,io.asv.mtgocr.ocrreader.data.LegacyCollectionStore.cards(context).map { it.collectionItemId }.sorted())
    }
}
