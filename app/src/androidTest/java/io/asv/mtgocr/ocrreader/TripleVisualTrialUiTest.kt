package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.widget.CheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripleVisualTrialUiTest {
    @Test fun flagsAreIndependentPersistAndInvalidatePendingEvidence() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=ctx.getSharedPreferences("triple_scanner",0);val saved=prefs.all
        fun field(name:String)=TripleScanActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
        prefs.edit().putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false)
            .remove("edscan_contour").putBoolean("single_art_reading",false).putBoolean("delayed_art_crop",false).commit()
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(ctx,TripleScanActivity::class.java).putExtra("triple_diagnostic_only",true)).use { scenario ->
                scenario.onActivity { a ->
                    val one=a.window.decorView.findViewWithTag<CheckBox>("triple_trial_single_art_reading")
                    val crop=a.window.decorView.findViewWithTag<CheckBox>("triple_trial_delayed_art_crop")
                    assertFalse(one.isChecked);assertFalse(crop.isChecked)
                    val before=field("generation").get(a)
                    val outline=a.window.decorView.findViewWithTag<CheckBox>("triple_contour_flag")
                    assertFalse(outline.isChecked)
                    outline.isChecked=true
                    assertTrue(field("contourEnabled").getBoolean(a))
                    assertTrue(prefs.getBoolean("edscan_contour",false))
                    assertEquals(before,field("generation").get(a)) // display flag must not reset recognition
                    one.isChecked=true
                    assertNotEquals(before,field("generation").get(a))
                    assertTrue(field("singleArtReading").getBoolean(a));assertFalse(field("delayedArtCrop").getBoolean(a))
                    crop.isChecked=true
                    assertTrue(field("delayedArtCrop").getBoolean(a))
                    assertTrue(prefs.getBoolean("single_art_reading",false));assertTrue(prefs.getBoolean("delayed_art_crop",false))
                    for(method in listOf("arEnabled","artEnabled","ocrEnabled")) assertFalse(field(method).getBoolean(a))
                }
                scenario.recreate()
                scenario.onActivity { a ->
                    assertTrue(field("contourEnabled").getBoolean(a))
                    a.window.decorView.findViewWithTag<CheckBox>("triple_contour_flag").isChecked=false
                    assertFalse(field("contourEnabled").getBoolean(a))
                    assertTrue(field("singleArtReading").getBoolean(a));assertTrue(field("delayedArtCrop").getBoolean(a))
                }
            }
        } finally {
            val e=prefs.edit().clear()
            for((k,v) in saved) when(v) {
                is Boolean -> e.putBoolean(k,v)
                is String -> e.putString(k,v)
                is Int -> e.putInt(k,v)
                is Long -> e.putLong(k,v)
                is Float -> e.putFloat(k,v)
                is Set<*> -> e.putStringSet(k,v.filterIsInstance<String>().toSet())
            }
            e.commit()
        }
    }
}
