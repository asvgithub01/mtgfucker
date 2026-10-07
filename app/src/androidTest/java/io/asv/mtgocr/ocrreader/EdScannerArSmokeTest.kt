package io.asv.mtgocr.ocrreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ar.core.ArCoreApk
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** AR camera + real EdScan inference smoke test. Debug no-save mode never writes the collection. */
class EdScannerArSmokeTest {
    @Test fun preloadsLatestBatchWithoutEdScanOrCollectionWrites() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(!(context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
        assumeTrue(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
        assumeTrue(ArCoreApk.getInstance().checkAvailability(context).isSupported)
        val before=io.asv.mtgocr.ocrreader.data.LegacyCollectionStore.cards(context)
        val set=InstrumentationRegistry.getArguments().getString("arSet")
        val requested=InstrumentationRegistry.getArguments().getString("arBatchGroup")
        val group=requested ?: context.getSharedPreferences("edscan_ar",android.content.Context.MODE_PRIVATE)
            .getString("preload_group_${LibraryCatalog.activeFile(context)}",null) ?: ArPreloadBatch.latestGroup(before)
        assumeTrue(set=="FRA" || group!=null)
        val expected=if(set=="FRA") {
            assumeTrue(PremiumAccess.isEnabled(context))
            ArPackageStore.installed(context,set)?.entry?.count ?: 0
        } else ArPreloadBatch.select(before,requireNotNull(group)).size
        assumeTrue(expected>0 && expected<=1000)
        val ids=before.map { it.collectionItemId }.sorted()
        ActivityScenario.launch<EdScannerArActivity>(Intent(context,EdScannerArActivity::class.java)
            .putExtra("edscan_test_no_save",true).putExtra("edscan_preload_last",true)
            .apply { if(set!="FRA") putExtra("edscan_preload_group",group) else putExtra("ar_package_id",set) }).use { scenario ->
            var ready=0
            val deadline=SystemClock.elapsedRealtime()+1200000
            while(ready==0 && SystemClock.elapsedRealtime()<deadline) {
                scenario.onActivity { activity ->
                    val preview=EdScannerArActivity::class.java.getDeclaredField("arPreview").apply { isAccessible=true }.get(activity)
                    ready=EdScannerArPreview::class.java.getDeclaredField("batchReadyCount").apply { isAccessible=true }.getInt(preview)
                    assertEquals("Preloaded AR must not invoke EdScan inference",0,
                        io.asv.collectorvision.NativeCollectorVisionActivity::class.java.getDeclaredField("externalAnalyzedFrames").apply { isAccessible=true }.getInt(activity))
                }
                if(ready==0) Thread.sleep(250)
            }
            assertEquals("Every reference in the latest batch must be ready",expected,ready)
            scenario.onActivity { activity ->
                val preview=EdScannerArActivity::class.java.getDeclaredField("arPreview").apply { isAccessible=true }.get(activity)
                val surface=EdScannerArPreview::class.java.getDeclaredField("camera").apply { isAccessible=true }.get(preview) as EdScannerArSurface
                assertTrue(surface.preloadMode)
                assertEquals(expected,surface.preloadedCards.size)
                assertEquals(0,surface.cpuFrames)
            }
        }
        assertEquals(ids,io.asv.mtgocr.ocrreader.data.LegacyCollectionStore.cards(context).map { it.collectionItemId }.sorted())
    }

    @Test fun preparesReferenceRendersCameraAndResumesWithoutSavingCards() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Unlock the device before the camera smoke test", !(context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
        assumeTrue(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
        assumeTrue(ArCoreApk.getInstance().checkAvailability(context).isSupported)
        val reference=File(context.cacheDir,"ar-smoke-reference.png")
        val bitmap=Bitmap.createBitmap(504,704,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG); val random=Random(913)
        repeat(450) {
            paint.color=Color.rgb(random.nextInt(220),random.nextInt(220),random.nextInt(220))
            canvas.drawCircle(random.nextFloat()*504,random.nextFloat()*704,3f+random.nextFloat()*25,paint)
        }
        reference.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        val intent=Intent()
            .putExtra("image",reference.toURI().toString()).putExtra("printing","ar-smoke-reference")
            .putExtra("label","AR smoke test · no collection writes").putExtra("price","TEST · 1.00 EUR")
        try {
            ActivityScenario.launch<EdScannerArActivity>(Intent(context,EdScannerArActivity::class.java)
                .putExtra("edscan_test_no_save",true).putExtra("edscan_preload_last",false)).use { scenario ->
                fun preview(activity: EdScannerArActivity)=EdScannerArActivity::class.java.getDeclaredField("arPreview").apply { isAccessible=true }.get(activity)
                scenario.onActivity { activity ->
                    (preview(activity) as EdScannerArPreview).updateTarget(intent)
                    assertNotNull(preview(activity))
                }
                fun awaitCamera() {
                    val deadline=SystemClock.elapsedRealtime()+90000
                    var running=false; var rendered=false; var analyzed=false
                    while(SystemClock.elapsedRealtime()<deadline && !(running && rendered && analyzed)) {
                        scenario.onActivity { activity ->
                            running=EdScannerArPreview::class.java.getDeclaredField("running").apply { isAccessible=true }.getBoolean(preview(activity))
                            val surface=EdScannerArPreview::class.java.getDeclaredField("camera").apply { isAccessible=true }.get(preview(activity))
                            analyzed=io.asv.collectorvision.NativeCollectorVisionActivity::class.java.getDeclaredField("externalAnalyzedFrames").apply { isAccessible=true }.getInt(activity)>0
                            assertNull(io.asv.collectorvision.NativeCollectorVisionActivity::class.java.getDeclaredField("provider").apply { isAccessible=true }.get(activity))
                            rendered=EdScannerArSurface::class.java.getDeclaredField("tracking").apply { isAccessible=true }.get(surface)!=null
                        }
                        if(!(running && rendered && analyzed)) Thread.sleep(200)
                    }
                    assertTrue("ARCore did not start; check permission/support/reference or camera errors",running)
                    assertTrue("ARCore did not render a camera frame",rendered)
                    assertTrue("ARCore CPU frame did not reach the EdScan engine",analyzed)
                }
                awaitCamera()
                val shaderDone=java.util.concurrent.CountDownLatch(1)
                val shaderError=java.util.concurrent.atomic.AtomicReference<Throwable?>()
                scenario.onActivity { activity ->
                    val surface=EdScannerArPreview::class.java.getDeclaredField("camera").apply { isAccessible=true }.get(preview(activity)) as EdScannerArSurface
                    surface.queueEvent {
                        try {
                            val shader=ArEnergyBorder()
                            try {
                                val matrix=FloatArray(16).also { android.opengl.Matrix.setIdentityM(it,0) }
                                shader.draw(matrix,matrix,.063f,.088f)
                                assertEquals(android.opengl.GLES20.GL_NO_ERROR,android.opengl.GLES20.glGetError())
                            } finally { shader.close() }
                            val reveal=ArCardReveal()
                            val texture=Bitmap.createBitmap(64,88,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
                            try {
                                reveal.upload(texture)
                                val matrix=FloatArray(16).also { android.opengl.Matrix.setIdentityM(it,0) }
                                reveal.draw(matrix,matrix,.063f,.088f,0f)
                                reveal.draw(matrix,matrix,.063f,.088f,2f)
                                assertEquals(android.opengl.GLES20.GL_NO_ERROR,android.opengl.GLES20.glGetError())
                            } finally { texture.recycle(); reveal.close() }
                        } catch(e: Throwable) { shaderError.set(e) }
                        finally { shaderDone.countDown() }
                    }
                }
                assertTrue("GPU shader test timed out",shaderDone.await(10,java.util.concurrent.TimeUnit.SECONDS))
                shaderError.get()?.let { throw AssertionError("GPU shader failed",it) }
                var contourSurface: EdScannerArSurface?=null
                var oldContour=false
                scenario.onActivity {
                    contourSurface=EdScannerArPreview::class.java.getDeclaredField("camera").apply { isAccessible=true }.get(preview(it)) as EdScannerArSurface
                    oldContour=contourSurface!!.genericOutlineEnabled
                    contourSurface!!.genericOutlineEnabled=true
                }
                try {
                    val processed=EdScannerArSurface::class.java.getDeclaredField("genericProcessedFrames").apply { isAccessible=true }
                    val deadline=SystemClock.elapsedRealtime()+10000
                    while(processed.getInt(contourSurface)==0 && SystemClock.elapsedRealtime()<deadline) Thread.sleep(100)
                    assertTrue("Generic contour worker must process real AR camera luma",processed.getInt(contourSurface)>0)
                } finally { contourSurface!!.genericOutlineEnabled=oldContour }
                val compiled=ArReferenceDiskCache(File(context.cacheDir,"ar-references"))
                    .file("ar-smoke-reference",reference.toURI().toString())
                val cacheDeadline=SystemClock.elapsedRealtime()+15000
                while(!compiled.isFile && SystemClock.elapsedRealtime()<cacheDeadline) Thread.sleep(100)
                assertTrue("Reference must be persisted for the next Session",compiled.isFile)
                scenario.recreate()
                scenario.onActivity { (preview(it) as EdScannerArPreview).updateTarget(intent) }
                awaitCamera()
                var diskHit=false
                val hitDeadline=SystemClock.elapsedRealtime()+15000
                while(!diskHit && SystemClock.elapsedRealtime()<hitDeadline) {
                    scenario.onActivity {
                        diskHit=EdScannerArPreview::class.java.getDeclaredField("diskReferenceHits").apply { isAccessible=true }.getInt(preview(it))>0
                    }
                    if(!diskHit) Thread.sleep(100)
                }
                assertTrue("New Session must reuse serialized reference",diskHit)
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.onActivity { activity ->
                    assertFalse(EdScannerArPreview::class.java.getDeclaredField("running").apply { isAccessible=true }.getBoolean(preview(activity)))
                }
                scenario.moveToState(Lifecycle.State.RESUMED); awaitCamera()
                scenario.onActivity { activity ->
                    val slot=EdScannerArActivity::class.java.getDeclaredField("arSlot").apply { isAccessible=true }.get(activity) as android.view.View
                    assertTrue("Camera must occupy most of the screen",slot.height>activity.window.decorView.height*.65)
                    val root=activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                    val recent=root.findViewWithTag<android.view.View>("ar_recent")
                    assertTrue("Recent gallery must remain visible",recent.isShown)
                    assertTrue(recent.height>0)
                    val options=root.findViewWithTag<android.view.View>("ar_options")
                    val toggle=root.findViewWithTag<android.view.View>("ar_options_toggle")
                    assertEquals(android.view.View.GONE,options.visibility)
                    val sessionField=EdScannerArPreview::class.java.getDeclaredField("session").apply { isAccessible=true }
                    val before=sessionField.get(preview(activity))
                    toggle.performClick(); assertEquals(android.view.View.VISIBLE,options.visibility)
                    assertTrue(recent.isShown)
                    val effect=root.findViewWithTag<android.widget.CheckBox>("ar_energy_border")
                    val wasEnabled=effect.isChecked
                    effect.performClick()
                    val surface=EdScannerArPreview::class.java.getDeclaredField("camera").apply { isAccessible=true }.get(preview(activity)) as EdScannerArSurface
                    assertEquals(!wasEnabled,surface.energyBorderEnabled)
                    effect.performClick()
                    assertEquals(wasEnabled,surface.energyBorderEnabled)
                    for(key in listOf("generic_outline","reveal")) {
                        val flag=root.findViewWithTag<android.widget.CheckBox>("ar_$key")
                        val old=flag.isChecked
                        flag.performClick()
                        assertEquals(!old,if(key=="reveal") surface.revealEnabled else surface.genericOutlineEnabled)
                        flag.performClick()
                        assertEquals(old,if(key=="reveal") surface.revealEnabled else surface.genericOutlineEnabled)
                    }
                    toggle.performClick(); assertEquals(android.view.View.GONE,options.visibility)
                    assertTrue(recent.isShown)
                    val generationField=EdScannerArPreview::class.java.getDeclaredField("targetGeneration").apply { isAccessible=true }
                    val generation=generationField.getInt(preview(activity))
                    (preview(activity) as EdScannerArPreview).updateTarget(Intent(intent).putExtra("price","TEST · 2.00 EUR"))
                    assertEquals("Price changes must preserve reference generation",generation,generationField.getInt(preview(activity)))
                    assertSame("Reference changes must not recreate AR camera",before,sessionField.get(preview(activity)))

                }
            }
        } finally { reference.delete() }
    }
}
