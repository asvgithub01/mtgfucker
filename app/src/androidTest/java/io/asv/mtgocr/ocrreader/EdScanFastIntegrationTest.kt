package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** UI/lifecycle only in emulator; diagnostic flag forbids synthetic inventory writes. */
@RunWith(AndroidJUnit4::class)
class EdScanFastIntegrationTest {
    @Test fun isolatedCameraModeDoesNotChangeOtherScannerSettingsOrInventory() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Emulator only" }
        val test=InstrumentationRegistry.getInstrumentation();val context=test.targetContext
        fun hashes()=context.filesDir.listFiles().orEmpty().filter { it.extension.equals("Json",true) }
            .associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val before=hashes()
        val preferenceNames=listOf("triple_scanner","cornelius_scanner","triple_ar")
        val prefs=preferenceNames.associateWith { context.getSharedPreferences(it,0).all }
        ActivityScenario.launch<EdScanFastActivity>(Intent(context,EdScanFastActivity::class.java)
            .putExtra("triple_diagnostic_only",true)).use { scene ->
            var frames=0
            val until=android.os.SystemClock.elapsedRealtime()+120000
            while(frames==0 && android.os.SystemClock.elapsedRealtime()<until) {
                scene.onActivity { a -> frames=EdScanFastActivity::class.java.getDeclaredField("analyzedFrames").apply { isAccessible=true }.getInt(a) }
                if(frames==0) Thread.sleep(500)
            }
            assertTrue("Camera must produce a real inference frame",frames>0)
            val photos=File(context.filesDir,"edfast-diagnostics")
            val previousPhotos=photos.listFiles().orEmpty().map { it.name }.toSet()
            scene.onActivity { a ->
                val root=a.findViewById<android.view.ViewGroup>(android.R.id.content)
                assertNotNull(root.findViewWithTag<TextView>("edfast_name"))
                assertTrue(root.findViewWithTag<TextView>("edfast_saved").text.toString().contains("0"))
                assertNotEquals(a.getString(R.string.triple_engine_error),root.findViewWithTag<TextView>("edfast_status").text.toString())
                root.findViewWithTag<android.widget.Button>("edfast_capture").performClick()
            }
            val captureDeadline=android.os.SystemClock.elapsedRealtime()+15000
            fun captured()=photos.listFiles().orEmpty().any { it.extension=="jpg" && it.name !in previousPhotos && it.length()>0 }
            while(!captured() && android.os.SystemClock.elapsedRealtime()<captureDeadline) Thread.sleep(200)
            assertTrue("Unrecognized frames must support manual evidence capture",captured())
            test.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.cacheDir,"edfast-screen.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            scene.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scene.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            Thread.sleep(1000)
        }
        assertEquals(before,hashes())
        preferenceNames.forEach { assertEquals(prefs[it],context.getSharedPreferences(it,0).all) }
    }
}
