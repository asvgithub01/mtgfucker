package io.asv.mtgocr.ocrreader

import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** No network, camera or collection writes: host-shell checks, not model-performance tests. */
class CollectorVisionLabDeviceTest {
    @Test fun activityIsPrivateAndDeclared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(ComponentName(context, CollectorVisionLabActivity::class.java), 0)
        assertFalse(info.exported)
    }
    @Test fun isolatedHostWaitsForExplicitLoadAndCloses() {
        ActivityScenario.launch(CollectorVisionLabActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val web = activity.findViewById<WebView>(R.id.collectorVisionWeb)
                assertNull(web.url)
                assertTrue(web.settings.javaScriptEnabled)
                assertFalse(web.settings.allowFileAccess)
                assertFalse(web.settings.allowContentAccess)
                assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.settings.mixedContentMode)
                assertTrue(activity.findViewById<View>(R.id.collectorVisionStart).isShown)
                assertTrue(activity.findViewById<View>(R.id.collectorVisionBrowser).isShown)
                activity.findViewById<View>(R.id.collectorVisionClose).performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }
    @Test fun stoppingClearsPendingCameraRequest() {
        ActivityScenario.launch(CollectorVisionLabActivity::class.java).use { scenario ->
            var denied = false
            scenario.onActivity { activity ->
                val request = object : android.webkit.PermissionRequest() {
                    override fun getOrigin() = android.net.Uri.parse("https://hanclinto.github.io")
                    override fun getResources() = arrayOf(RESOURCE_VIDEO_CAPTURE)
                    override fun grant(resources: Array<out String>?) { fail("Must not grant on exit") }
                    override fun deny() { denied = true }
                }
                CollectorVisionLabActivity::class.java.getDeclaredField("pendingCamera").apply {
                    isAccessible = true
                    set(activity, request)
                }
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertTrue(denied)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val pending = CollectorVisionLabActivity::class.java.getDeclaredField("pendingCamera").apply { isAccessible = true }
                assertNull(pending.get(activity))
            }
        }
    }
}
