package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ScannerEntrypointsTest {
    @Test fun libraryOnlyShowsOriginalOcrAndIconOnlyTripleAcrossNavigation() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val test=InstrumentationRegistry.getInstrumentation();val ctx=test.targetContext
        ActivityScenario.launch<OcrCaptureActivity>(Intent(ctx,OcrCaptureActivity::class.java)
            .putExtra(App.INTENT_PERSISTOR_MODE,"0")).use { scene ->
            Thread.sleep(700)
            for(destination in listOf(R.id.nav_library,R.id.nav_settings,R.id.nav_library,R.id.nav_catalog,R.id.nav_library)) {
                scene.onActivity { a ->
                    a.findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId=destination
                    for(id in listOf(R.id.fabOcr,R.id.fabRapidEditionScanner,R.id.fabHashOnlyScanner,
                        R.id.fabRulesScanner,R.id.fabCollectorVision,R.id.fabEdScannerAr))
                        assertEquals(View.GONE,a.findViewById<View>(id).visibility)
                    val expected=if(destination==R.id.nav_library) View.VISIBLE else View.GONE
                    val triple=a.findViewById<View>(R.id.fabTripleScanner)
                    assertTrue(triple is FloatingActionButton)
                    assertEquals(a.getString(R.string.triple_title),triple.contentDescription)
                    assertEquals(expected,triple.visibility)
                    assertEquals(expected,a.findViewById<View>(R.id.fabOcrMlKit).visibility)
                    assertTrue(triple.hasOnClickListeners())
                    assertTrue(a.findViewById<View>(R.id.fabOcrMlKit).hasOnClickListeners())
                }
                Thread.sleep(250)
            }
            scene.onActivity { a ->
                val triple=a.findViewById<View>(R.id.fabTripleScanner)
                val ocr=a.findViewById<View>(R.id.fabOcrMlKit)
                assertTrue(triple.bottom < ocr.top)
                assertEquals(triple.right,ocr.right)
            }
            test.uiAutomation.takeScreenshot()?.let { bmp ->
                File(ctx.cacheDir,"biblio-two-scanners.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bmp.recycle()
            }
        }
    }
}
