package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.CheckBox
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TripleLaserTest {
    @Test fun flagMotionPersistenceAndLifecycle() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val test=InstrumentationRegistry.getInstrumentation()
        val ctx=test.targetContext
        val prefs=ctx.getSharedPreferences("triple_scanner",0)
        val keys=listOf("ar","art","ocr","laser_animation")
        val old=keys.associateWith { if(prefs.contains(it)) prefs.getBoolean(it,false) else null }
        prefs.edit().putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false).remove("laser_animation").commit()
        var overlay:TripleLaserView?=null
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(ctx,TripleScanActivity::class.java)
                .putExtra("triple_diagnostic_only",true)).use { scene ->
                Thread.sleep(800)
                scene.onActivity { a ->
                    val root=a.findViewById<View>(android.R.id.content)
                    assertEquals(View.GONE,root.findViewWithTag<View>("triple_fast_mode").visibility)
                    overlay=root.findViewWithTag("triple_laser_overlay")
                    assertFalse(overlay!!.isAnimating)
                    assertEquals(View.GONE,overlay!!.visibility)
                    root.findViewWithTag<CheckBox>("triple_laser_flag").isChecked=true
                }
                Thread.sleep(400)
                var first=0f
                scene.onActivity {
                    assertTrue(overlay!!.isAnimating)
                    assertFalse(overlay!!.isClickable)
                    first=overlay!!.getChildAt(0).translationY
                }
                Thread.sleep(450)
                scene.onActivity {
                    assertNotEquals(first,overlay!!.getChildAt(0).translationY)
                    val line=overlay!!.getChildAt(0)
                    assertTrue(line.translationY >= 0 && line.translationY+line.height <= overlay!!.height)
                    val bmp=android.graphics.Bitmap.createBitmap(overlay!!.width,overlay!!.height,android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas=android.graphics.Canvas(bmp);canvas.drawColor(0xFF102030.toInt());overlay!!.draw(canvas)
                    File(ctx.cacheDir,"triple-laser-preview.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bmp.recycle()
                }
                scene.moveToState(Lifecycle.State.CREATED)
                assertFalse(overlay!!.isAnimating)
                scene.moveToState(Lifecycle.State.RESUMED)
                Thread.sleep(200)
                scene.onActivity { assertTrue(overlay!!.isAnimating) }
                scene.recreate();Thread.sleep(600)
                scene.onActivity { a ->
                    val root=a.findViewById<View>(android.R.id.content)
                    assertEquals(View.GONE,root.findViewWithTag<View>("triple_fast_mode").visibility)
                    overlay=root.findViewWithTag("triple_laser_overlay")
                    assertTrue(root.findViewWithTag<CheckBox>("triple_laser_flag").isChecked)
                    assertTrue(overlay!!.isAnimating)
                    root.findViewWithTag<CheckBox>("triple_laser_flag").isChecked=false
                    assertFalse(overlay!!.isAnimating)
                    assertFalse(prefs.getBoolean("laser_animation",true))
                }
            }
            assertFalse(overlay!!.isAnimating)
        } finally {
            val edit=prefs.edit();old.forEach { (key,value)->if(value==null) edit.remove(key) else edit.putBoolean(key,value) };edit.commit()
        }
    }
}
