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
    @Test fun embedsTheSameReviewRowOnlyForTheOwnedCard() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ctx=instrumentation.targetContext
        val premium=ctx.getSharedPreferences("premium_access",0)
        val wasPremium=premium.getBoolean("premium_enabled",false)
        val hadPremium=premium.contains("premium_enabled")
        val registry=ctx.getSharedPreferences("library_catalog",0)
        val oldLibraries=registry.getStringSet("libraries",emptySet())!!.toSet()
        val oldActive=registry.getString("active_library_id",null)
        val id=java.util.UUID.randomUUID().toString()
        val library="biblio_$id.Json"
        val file=File(ctx.filesDir,library)
        val dir=File(ctx.filesDir,"edscan").apply { mkdirs() }
        val keys=List(3) { java.util.UUID.randomUUID().toString() }
        val first=io.asv.mtgocr.ocrreader.model.CardInfo("Detail fixture target", "", "", "", "2").apply {
            finish="nonfoil"; languageCode="en"; setCode="ZZZ"
        }
        val second=io.asv.mtgocr.ocrreader.model.CardInfo("Detail fixture OTHER", "", "", "", "1").apply {
            finish="nonfoil"; languageCode="en"; setCode="ZZZ"
        }
        val biblio=io.asv.mtgocr.ocrreader.model.Biblio(library,"Detail fixture").apply { cards.add(first);cards.add(second) }
        java.io.ObjectOutputStream(file.outputStream()).use { it.writeObject(biblio) }
        val before=file.readBytes()
        fun textTree(v: View): List<String> = (if(v is TextView) listOf(v.text.toString()) else emptyList()) +
            (if(v is android.view.ViewGroup) (0 until v.childCount).flatMap { textTree(v.getChildAt(it)) } else emptyList())
        try {
            premium.edit().putBoolean("premium_enabled",true).commit()
            registry.edit().putStringSet("libraries",oldLibraries + "$id\tDetail fixture\t$library").putString("active_library_id",id).commit()
            keys.forEachIndexed { index,key ->
                File(dir,"$key.jpg").writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("triple-card.jpg").use { it.readBytes() })
                File(dir,"$key.link.json").writeText(JSONObject().put("library",if(index==2) "foreign" else library)
                    .put("collectionItemId",if(index==1) second.collectionItemId else first.collectionItemId).toString())
                File(dir,"$key.json").writeText("{\"source\":\"edscan\"}")
            }
            var fullRow=emptyList<String>()
            ActivityScenario.launch<ScanReviewActivity>(Intent(ctx,ScanReviewActivity::class.java)).use { scene ->
                Thread.sleep(800)
                scene.onActivity { a ->
                    val list=a.findViewById<android.widget.ListView>(R.id.reviewList)
                    assertEquals(2,list.adapter.count)
                    val entryPosition=(0 until list.adapter.count).first { (list.adapter.getItem(it) as ScanReviewStore.Entry).card.collectionItemId==first.collectionItemId }
                    fullRow=textTree(list.adapter.getView(entryPosition,null,list)).filter { it != a.getString(R.string.view_card_details) }
                }
            }
            ActivityScenario.launch<Main2Activity>(Intent(ctx,Main2Activity::class.java)
                .putExtra("cardName",first.name).putExtra("collectionItemId",first.collectionItemId).putExtra(Main2Activity.EXTRA_AS_POPUP,true)).use { scene ->
                Thread.sleep(800)
                scene.onActivity { a ->
                    val body=a.findViewById<android.widget.LinearLayout>(R.id.postprocessBody)
                    assertEquals(1,body.childCount)
                    val texts=textTree(body).filter { it != a.getString(R.string.view_card_details) }
                    assertEquals(fullRow,texts)
                    assertTrue(texts.any { it.contains(first.name) })
                    assertFalse(texts.any { it.contains(second.name) })
                    val row=(body.getChildAt(0) as android.widget.FrameLayout).getChildAt(0) as android.widget.LinearLayout
                    val header=row.getChildAt(0) as android.widget.LinearLayout
                    header.getChildAt(0).performClick()
                    assertEquals(View.GONE,row.getChildAt(1).visibility)
                    header.getChildAt(0).performClick()
                    assertEquals(View.VISIBLE,row.getChildAt(1).visibility)
                }
            }
            assertArrayEquals(before,file.readBytes())
            ActivityScenario.launch<Main2Activity>(Intent(ctx,Main2Activity::class.java)
                .putExtra("cardName",first.name).putExtra("collectionItemId",first.collectionItemId).putExtra("browseOnly",true)).use { scene ->
                scene.onActivity { a -> assertEquals(View.GONE,a.findViewById<View>(R.id.postprocessSection).visibility) }
            }
        } finally {
            registry.edit().putStringSet("libraries",oldLibraries).apply {
                if(oldActive==null) remove("active_library_id") else putString("active_library_id",oldActive)
            }.commit()
            premium.edit().apply { if(hadPremium) putBoolean("premium_enabled",wasPremium) else remove("premium_enabled") }.commit()
            file.delete()
            keys.forEach { key -> dir.listFiles().orEmpty().filter { it.name.startsWith(key) }.forEach { it.delete() } }
        }
    }

    @Test fun artworkUsesSourceAspectRatioAtFullWidth() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view=CardArtBackgroundView(instrumentation.targetContext)
            view.fitArtworkAspectRatio=true
            view.setArtworkOnly(true)
            for ((w,h) in listOf(800 to 400,600 to 450)) {
                val bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
                view.setImageDrawable(android.graphics.drawable.BitmapDrawable(view.resources,bitmap))
                for (width in listOf(360,720)) {
                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                    assertEquals(width,view.measuredWidth)
                    assertEquals(width*h/w,view.measuredHeight)
                }
            }
        }
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
                    .putExtra("browseOnly",false).putExtra(Main2Activity.EXTRA_AS_POPUP,true).putExtra("cardName","Detail geometry fixture not in catalog").putExtra("collectionItemId","test-non-owned-id")).use { scene ->
                    Thread.sleep(1000)
                    scene.onActivity { a ->
                        val section=a.findViewById<View>(R.id.postprocessSection)
                        assertEquals(if(premium) View.VISIBLE else View.GONE,section.visibility)
                        val header=a.findViewById<CardArtBackgroundView>(R.id.detailHeaderArt)
                        header.fitArtworkAspectRatio=true
                        header.setArtworkOnly(false)
                        val bitmap=InstrumentationRegistry.getInstrumentation().context.assets.open("triple-card.jpg").use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        }
                        header.setImageDrawable(android.graphics.drawable.BitmapDrawable(a.resources,bitmap))
                        val scroll=a.findViewById<NestedScrollView>(R.id.detailScroll)
                        val hero=a.findViewById<View>(R.id.detailHero)
                        assertEquals(View.GONE,header.visibility)
                        // Add enough content so tiny/large test displays both permit scrolling past hero.
                        a.findViewById<TextView>(R.id.txtCardRules).text="Rules\n".repeat(100)
                        scroll.post { scroll.scrollTo(0,hero.bottom+10) }
                    }
                    Thread.sleep(300)
                    scene.onActivity { a ->
                        val art=a.findViewById<CardArtBackgroundView>(R.id.detailHeaderArt)
                        assertEquals(View.VISIBLE,art.visibility)
                        assertEquals((art.parent as View).width,art.width)
                        assertTrue(art.height > 0)
                        assertEquals(art.width * art.drawable.intrinsicHeight.toFloat() / art.drawable.intrinsicWidth * (.47f-.105f)/(.935f-.065f),art.height.toFloat(),1f)
                        val top=IntArray(2); val controls=IntArray(2)
                        art.getLocationOnScreen(top)
                        val close=a.findViewById<View>(R.id.btnBack)
                        close.getLocationOnScreen(controls)
                        assertTrue(top[1] <= controls[1])
                        assertTrue(top[1] + art.height > controls[1] + close.height)
                        val decor=a.window.decorView
                        val bitmap=android.graphics.Bitmap.createBitmap(decor.width,decor.height,android.graphics.Bitmap.Config.ARGB_8888)
                        decor.draw(android.graphics.Canvas(bitmap))
                        File(a.cacheDir,"detail-art-scroll.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                        bitmap.recycle()
                        a.findViewById<NestedScrollView>(R.id.detailScroll).scrollTo(0,0)
                        assertEquals(View.GONE,a.findViewById<View>(R.id.detailHeaderArt).visibility)
                    }
                }
            }
        } finally { prefs.edit().apply { if(had) putBoolean("premium_enabled",before) else remove("premium_enabled") }.commit() }
    }
}
