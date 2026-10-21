package io.asv.mtgocr.ocrreader

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class StorageAutomaticSilenceTest {
    @Test fun automaticCleanupAndLegacyResultsNeverOpenDialogs() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val test=InstrumentationRegistry.getInstrumentation()
        val ctx=test.targetContext
        val history=ctx.getSharedPreferences("storage_results",0)
        val manual=ctx.getSharedPreferences("storage_manual_results",0)
        manual.edit().clear().commit()
        history.edit().putString("pending","legacy automatic result").commit()
        ActivityScenario.launch(StorageActivity::class.java).use { scene ->
            Thread.sleep(700)
            fun assertQuiet() {
                test.waitForIdleSync()
                val root=test.uiAutomation.rootInActiveWindow
                assertNotNull(root)
                for(id in listOf(R.string.storage_deleting,R.string.storage_result)) {
                    val title=ctx.getString(id)
                    // Accessibility text search is substring-based; the Settings button
                    // "Last automatic cleanup result" is not a modal result title.
                    assertFalse(root!!.findAccessibilityNodeInfosByText(title).any { it.text?.toString()==title })
                }
            }
            assertQuiet()
            StorageOperations.automatic(ctx) { deleted ->
                deleted(1234L)
                Thread.sleep(200)
                assertQuiet()
                deleted(-1L)
            }
            Thread.sleep(200)
            assertQuiet()
            assertFalse(manual.contains("pending"))
            assertEquals(1234L,history.getLong("bytes",-1))
            assertEquals(1,history.getInt("failed",-1))
            scene.recreate()
            Thread.sleep(700)
            assertQuiet()
            // Empty passes must retain the last useful result for Settings.
            val previous=history.getString("pending",null)
            StorageOperations.automatic(ctx) {}
            assertEquals(previous,history.getString("pending",null))
        }
        history.edit().clear().commit()
    }
}
