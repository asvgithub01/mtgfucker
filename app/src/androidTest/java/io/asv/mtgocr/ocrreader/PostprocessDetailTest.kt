package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PostprocessDetailTest {
    @Test fun evidenceIsReadOnlyAndScopedToLibraryAndOwnedId() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(ctx.cacheDir,"detail-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            fun put(key:String,lib:String,item:String) {
                File(dir,"$key.link.json").writeText(JSONObject().put("library",lib).put("collectionItemId",item).toString())
                File(dir,"$key.language-resolution.json").writeText("{\"language\":\"pt\"}")
            }
            put("one","A","1");put("two","B","1");put("three","A","2")
            val before=dir.listFiles()!!.associate { it.name to it.readText() }
            val docs=PostprocessEvidenceStore.read(dir,"A","1")
            assertEquals(setOf("one.link.json","one.language-resolution.json"),docs.map { it.name }.toSet())
            assertTrue(PostprocessEvidenceStore.read(dir,"A","").isEmpty())
            assertEquals(before,dir.listFiles()!!.associate { it.name to it.readText() })
        } finally { dir.deleteRecursively() }
    }
    @Test fun detailHeaderTracksScrollAndPremiumSectionIsGated() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=ctx.getSharedPreferences("premium_access",0)
        val had=prefs.contains("premium_enabled");val before=prefs.getBoolean("premium_enabled",false)
        try {
            for(premium in listOf(false,true)) {
                prefs.edit().putBoolean("premium_enabled",premium).commit()
                ActivityScenario.launch<Main2Activity>(Intent(ctx,Main2Activity::class.java)
                    .putExtra("browseOnly",true).putExtra("cardName","Northern Paladin").putExtra("collectionItemId","test-non-owned-id")).use { scene ->
                    Thread.sleep(1000)
                    scene.onActivity { a ->
                        val section=a.findViewById<View>(R.id.postprocessSection)
                        assertEquals(if(premium) View.VISIBLE else View.GONE,section.visibility)
                        val header=a.findViewById<View>(R.id.detailHeaderArt)
                        val scroll=a.findViewById<NestedScrollView>(R.id.detailScroll)
                        val hero=a.findViewById<View>(R.id.detailHero)
                        assertEquals(View.GONE,header.visibility)
                        // Add enough content so tiny/large test displays both permit scrolling past hero.
                        a.findViewById<TextView>(R.id.txtCardRules).text="Rules\n".repeat(100)
                        scroll.post { scroll.scrollTo(0,hero.bottom+10) }
                    }
                    Thread.sleep(300)
                    scene.onActivity { a ->
                        assertEquals(View.VISIBLE,a.findViewById<View>(R.id.detailHeaderArt).visibility)
                        a.findViewById<NestedScrollView>(R.id.detailScroll).scrollTo(0,0)
                        assertEquals(View.GONE,a.findViewById<View>(R.id.detailHeaderArt).visibility)
                    }
                }
            }
        } finally { prefs.edit().apply { if(had) putBoolean("premium_enabled",before) else remove("premium_enabled") }.commit() }
    }
}
