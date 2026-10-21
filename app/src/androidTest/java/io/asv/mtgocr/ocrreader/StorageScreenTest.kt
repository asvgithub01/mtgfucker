package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StorageScreenTest {
    @Test fun fiveBlocksAndMeasuredDeletionResultAreVisible() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val test=InstrumentationRegistry.getInstrumentation();val ctx=test.targetContext
        val resultPrefs=ctx.getSharedPreferences("storage_manual_results",0);resultPrefs.edit().remove("pending").commit()
        for(command in listOf("input keyevent KEYCODE_WAKEUP","wm dismiss-keyguard")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(test.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        fun texts(v:View):List<String> = (if(v is TextView) listOf(v.text.toString()) else emptyList())+
            (if(v is android.view.ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList())
        val file=File(ctx.cacheDir,"storage-ui-fixture.tmp").apply { writeBytes(ByteArray(10000)) }
        try {
            ActivityScenario.launch<StorageActivity>(Intent(ctx,StorageActivity::class.java)).use { scene ->
                var ready=false
                val deadline=android.os.SystemClock.elapsedRealtime()+20000
                while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) {
                    Thread.sleep(100)
                    scene.onActivity { a -> ready=texts(a.findViewById(android.R.id.content)).contains(a.getString(R.string.storage_photos)) }
                }
                assertTrue("All five sections should load",ready)
                scene.onActivity { a ->
                    val content=a.findViewById<android.view.ViewGroup>(android.R.id.content)
                    assertTrue(content.getChildAt(0) is CardArtBackgroundView)
                    val found=texts(content)
                    listOf(R.string.storage_images,R.string.storage_postprocess,R.string.storage_catalog,R.string.storage_scanner,R.string.storage_photos).forEach { assertTrue(found.contains(a.getString(it))) }
                }
                test.uiAutomation.takeScreenshot()?.let { screenshot ->
                    File(ctx.cacheDir,"storage-screen.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    screenshot.recycle()
                }
                val done=CountDownLatch(1)
                val proceed=CountDownLatch(1)
                scene.onActivity { a -> StorageOperations.perform(a,{report,progress->
                    proceed.await(5,TimeUnit.SECONDS);StorageData.deleteFile(file,report);progress();Thread.sleep(200)
                },{done.countDown()}) }
                val until=android.os.SystemClock.elapsedRealtime()+4000
                var visible=false
                while(!visible && android.os.SystemClock.elapsedRealtime()<until) {
                    Thread.sleep(100)
                    visible=test.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(ctx.getString(R.string.storage_deleting))?.isNotEmpty()==true
                }
                proceed.countDown()
                assertTrue("Deletion loading should be visible",visible)
                assertTrue(done.await(10,TimeUnit.SECONDS));Thread.sleep(500)
                val result=test.uiAutomation.rootInActiveWindow
                assertNotNull(result)
                assertTrue(result!!.findAccessibilityNodeInfosByText(ctx.getString(R.string.storage_result)).isNotEmpty())
                assertTrue(result.findAccessibilityNodeInfosByText(StorageOperations.mb(10000)).isNotEmpty())
                assertFalse(file.exists())
            }
        } finally { file.delete();resultPrefs.edit().remove("pending").commit() }
    }
}
