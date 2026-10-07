package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@org.junit.Ignore("Legacy no-write fixtures: migrate to isolated collection context before running. Covered persistence/menu: TripleCollectionSessionTest.")
@RunWith(AndroidJUnit4::class)
class TripleScanSmokeTest {
    private var savedMethods=emptyMap<String,Boolean?>()
    @org.junit.Before fun enableMethodsForFixture() {
        val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("triple_scanner",0)
        savedMethods=listOf("ar","art","ocr").associateWith { if(prefs.contains(it)) prefs.getBoolean(it,true) else null }
        prefs.edit().putBoolean("ar",true).putBoolean("art",true).putBoolean("ocr",true).commit()
    }
    @org.junit.After fun restoreUserMethods() {
        val edit=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("triple_scanner",0).edit()
        savedMethods.forEach { (key,value)-> if(value==null) edit.remove(key) else edit.putBoolean(key,value) };edit.commit()
    }

    private fun field(value:Any,name:String):Any?=value.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(value)
    private fun set(value:Any,name:String,new:Any) { value.javaClass.getDeclaredField(name).apply { isAccessible=true }.set(value,new) }
    private fun waitFor(scenario:ActivityScenario<TripleScanActivity>,predicate:(TripleScanActivity)->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+90000;var ready=false
        while(!ready && SystemClock.elapsedRealtime()<deadline) { scenario.onActivity { ready=predicate(it) };if(!ready) Thread.sleep(100) }
        assertTrue("Triple stage timed out",ready)
    }
    @Test fun globalCameraOcrDynamicReferenceAndConfettiWithoutCollectionWrites() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val c=instrumentation.targetContext
        assumeTrue(!(c.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
        val before=io.asv.mtgocr.ocrreader.data.LegacyCollectionStore.cards(c).map { it.collectionItemId }.sorted()
        val oldPreferences=c.getSharedPreferences("edscan_ar",0).all.toMap()
        ActivityScenario.launch<TripleScanActivity>(Intent(c,TripleScanActivity::class.java).putExtra("triple_set","")).use { scenario ->
            waitFor(scenario) { (field(it,"visualFrames") as Int)>0 }
            scenario.onActivity { activity ->
                val multi=activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<android.widget.CheckBox>("ar_multiple_cards")
                val layer=activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<android.widget.CheckBox>("ar_image_layer")
                val oldMulti=multi.isChecked;val oldLayer=layer.isChecked
                multi.isChecked=true;layer.isChecked=true
                assertEquals(true,field(field(field(activity,"preview")!!,"camera")!!,"multipleCardsEnabled"))
                assertEquals(true,field(field(field(activity,"preview")!!,"overlay")!!,"imageEnabled"))
                multi.isChecked=oldMulti;layer.isChecked=oldLayer
                val content=activity.findViewById<android.view.View>(android.R.id.content)
                val ar=content.findViewWithTag<android.widget.CheckBox>("triple_method_ar")
                val art=content.findViewWithTag<android.widget.CheckBox>("triple_method_art")
                val ocr=content.findViewWithTag<android.widget.CheckBox>("triple_method_ocr")
                val saved=listOf(ar.isChecked,art.isChecked,ocr.isChecked)
                try {
                    for(mask in 0..7) {
                        ar.isChecked=mask and 1!=0;art.isChecked=mask and 2!=0;ocr.isChecked=mask and 4!=0
                        assertEquals(ar.isChecked,field(activity,"arEnabled"))
                        assertEquals(art.isChecked,field(activity,"artEnabled"))
                        assertEquals(ocr.isChecked,field(activity,"ocrEnabled"))
                        assertEquals(ar.isChecked,field(field(field(activity,"preview")!!,"camera")!!,"recognitionEnabled"))
                    }
                } finally { ar.isChecked=saved[0];art.isChecked=saved[1];ocr.isChecked=saved[2] }

            }

            scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor(scenario) { (field(it,"visualFrames") as Int)>1 }
            // Freeze camera analysis only while injecting a known rectified OCR fixture.
            scenario.onActivity { set(it,"lastFrameAt",SystemClock.elapsedRealtime()+60000) }
            waitFor(scenario) { !(field(it,"busy") as java.util.concurrent.atomic.AtomicBoolean).get() && !(field(it,"ocrScheduled") as java.util.concurrent.atomic.AtomicBoolean).get() }
            val meta=org.json.JSONObject(instrumentation.context.assets.open("triple-card.json").bufferedReader().use { it.readText() })
            val ref=meta.getJSONArray("references").getJSONObject(0)
            val id=ref.getString("scryfallId")
            val target=TripleScanActivity.Target(id,ref.getString("printing"),ref.getString("label").substringBefore(" · "),ref.getString("setCode"),ref.getString("label"),ref.getString("image"),ref.getString("finish"),"EdScan")
            val local=java.io.File(c.cacheDir,"triple-smoke.jpg").apply { writeBytes(instrumentation.context.assets.open("triple-card.jpg").use { it.readBytes() }) }
            scenario.onActivity { activity ->
                set(activity,"frameKey",id)
                TripleScanActivity::class.java.getDeclaredMethod("accept",TripleScanActivity.Target::class.java).apply { isAccessible=true }.invoke(activity,target)
                val original=BitmapFactory.decodeFile(local.path)
                val image=android.graphics.Bitmap.createScaledBitmap(original,630,880,true);if(image!==original) original.recycle()
                TripleScanActivity::class.java.getDeclaredMethod("ocr",android.graphics.Bitmap::class.java,TripleScanActivity.Target::class.java,Int::class.javaPrimitiveType,String::class.java).apply { isAccessible=true }
                    .invoke(activity,image,target,field(activity,"generation"),id)
                (field(activity,"preview") as TripleArPreview).updateTarget(Intent().putExtra("printing",target.printing).putExtra("image",local.toURI().toString()).putExtra("price","1.00 EUR").putExtra("pricePending",false).putExtra("rarity","rare"))
            }
            waitFor(scenario) { (field(it,"ocrCompleted") as Int)>0 && !(field(it,"ocrScheduled") as java.util.concurrent.atomic.AtomicBoolean).get() }
            // A second independent capture confirms physical language; one reading is provisional.
            scenario.onActivity { activity ->
                set(activity,"lastOcrAt",0L)
                val original=BitmapFactory.decodeFile(local.path)
                val image=android.graphics.Bitmap.createScaledBitmap(original,630,880,true);if(image!==original) original.recycle()
                TripleScanActivity::class.java.getDeclaredMethod("ocr",android.graphics.Bitmap::class.java,TripleScanActivity.Target::class.java,Int::class.javaPrimitiveType,String::class.java).apply { isAccessible=true }
                    .invoke(activity,image,target,field(activity,"generation"),id)
            }
            waitFor(scenario) { (field(it,"ocrCompleted") as Int)>1 && !(field(it,"ocrScheduled") as java.util.concurrent.atomic.AtomicBoolean).get() }
            waitFor(scenario) { val preview=field(it,"preview")!!; (field(field(preview,"camera")!!,"targetName") as String)==target.printing }
            scenario.onActivity { activity ->
                val preview=field(activity,"preview")!!
                assertEquals(TripleRarityColor.color("rare"),field(field(preview,"overlay")!!,"borderColor"))
                val label=(field(activity,"result") as android.widget.TextView).text.toString()
                assertTrue(label,label.contains("en"))
                val gallery=field(activity,"recent") as android.widget.LinearLayout
                assertTrue(gallery.childCount>0)
                val row=gallery.getChildAt(0)
                assertEquals(CardLanguageFlag.label("en"),row.findViewWithTag<android.widget.TextView>("triple_gallery_language").text.toString())
                assertEquals(target.set.uppercase(),row.findViewWithTag<android.widget.TextView>("triple_gallery_edition").text.toString().substringBefore(" #"))
                assertEquals("en",(field(field(preview,"overlay")!!,"languages") as Map<*,*>)[target.printing])
                val count=gallery.childCount
                set(activity,"prices",mapOf(id to (1.25 to "EUR")))
                TripleScanActivity::class.java.getDeclaredMethod("refreshGallery").apply { isAccessible=true }.invoke(activity)
                assertEquals("%.2f EUR".format(1.25),row.findViewWithTag<android.widget.TextView>("triple_gallery_price").text.toString())
                assertEquals(count,gallery.childCount)
            }
            // Regression: a glare/empty-rules snapshot must not erase the confirmed flag.
            val completedBefore=intArrayOf(0)
            scenario.onActivity { activity ->
                completedBefore[0]=field(activity,"ocrCompleted") as Int
                set(activity,"lastOcrAt",0L)
                val blank=android.graphics.Bitmap.createBitmap(630,880,android.graphics.Bitmap.Config.ARGB_8888)
                blank.eraseColor(android.graphics.Color.WHITE)
                TripleScanActivity::class.java.getDeclaredMethod("ocr",android.graphics.Bitmap::class.java,TripleScanActivity.Target::class.java,Int::class.javaPrimitiveType,String::class.java).apply { isAccessible=true }
                    .invoke(activity,blank,target,field(activity,"generation"),id)
            }
            waitFor(scenario) { (field(it,"ocrCompleted") as Int)>completedBefore[0] && !(field(it,"ocrScheduled") as java.util.concurrent.atomic.AtomicBoolean).get() }
            scenario.onActivity { activity ->
                assertEquals("en",field(activity,"observedLanguage"))
                val gallery=field(activity,"recent") as android.widget.LinearLayout
                assertEquals(CardLanguageFlag.label("en"),gallery.getChildAt(0).findViewWithTag<android.widget.TextView>("triple_gallery_language").text.toString())
                assertEquals("en",(field(field(field(activity,"preview")!!,"overlay")!!,"languages") as Map<*,*>)[target.printing])
            }
            // Same printing, NEW encounter: preserve historical language and start another row unknown.
            scenario.onActivity { activity ->
                val gallery=field(activity,"recent") as android.widget.LinearLayout
                val old=gallery.getChildAt(0);val count=gallery.childCount
                val gate=field(activity,"encounters") as TripleEncounterGate
                gate.missing(100);gate.missing(1200)
                TripleScanActivity::class.java.getDeclaredMethod("accept",TripleScanActivity.Target::class.java).apply { isAccessible=true }.invoke(activity,target)
                assertEquals(count+1,gallery.childCount)
                assertEquals("?",gallery.getChildAt(0).findViewWithTag<android.widget.TextView>("triple_gallery_language").text.toString())
                assertEquals(CardLanguageFlag.label("en"),old.findViewWithTag<android.widget.TextView>("triple_gallery_language").text.toString())
            }
            // OCR rescue reads a title crop without requiring a detected card quadrilateral.
            scenario.onActivity { activity ->
                val original=BitmapFactory.decodeFile(local.path)
                val title=android.graphics.Bitmap.createBitmap(original,0,0,original.width,(original.height*.19f).toInt())
                if(title!==original) original.recycle()
                TripleScanActivity::class.java.getDeclaredMethod("guidedOcr",android.graphics.Bitmap::class.java,Int::class.javaPrimitiveType).apply { isAccessible=true }
                    .invoke(activity,title,field(activity,"generation"))
            }
            waitFor(scenario) { !(field(it,"ocrScheduled") as java.util.concurrent.atomic.AtomicBoolean).get() && !(field(it,"guideLookupPending") as Boolean) }
            scenario.onActivity { activity ->
                val guide=field(field(activity,"preview")!!,"titleGuide")!!
                assertNotNull("Local OCR title should be recognized",field(guide,"caption"))
            }
            val drawn=CountDownLatch(1);var failure:Throwable?=null
            scenario.onActivity { activity ->
                val surface=field(field(activity,"preview")!!,"camera") as TripleArSurface
                surface.queueEvent {
                    try {
                        val effect=TripleConfetti();val vp=FloatArray(16);val pose=FloatArray(16)
                        android.opengl.Matrix.setIdentityM(vp,0);android.opengl.Matrix.setIdentityM(pose,0)
                        effect.draw(vp,pose,.8f);effect.close()
                        val border=TripleEnergyBorder()
                        border.draw(vp,pose,.063f,.088f,TripleRarityColor.color("mythic"))
                        // Pixel test: smoke must remain visible on a WHITE camera-like background.
                        val viewport=IntArray(4);android.opengl.GLES20.glGetIntegerv(android.opengl.GLES20.GL_VIEWPORT,viewport,0)
                        try {
                            android.opengl.GLES20.glViewport(0,0,256,256)
                            android.opengl.GLES20.glClearColor(1f,1f,1f,1f)
                            android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT)
                            android.opengl.Matrix.setRotateM(pose,0,90f,1f,0f,0f)
                            border.draw(vp,pose,1f,1f,TripleRarityColor.color("common"))
                            val pixels=java.nio.ByteBuffer.allocateDirect(256*256*4)
                            android.opengl.GLES20.glReadPixels(0,0,256,256,android.opengl.GLES20.GL_RGBA,android.opengl.GLES20.GL_UNSIGNED_BYTE,pixels)
                            var smokePixels=0
                            for(y in 70..185) for(x in 202..220) {
                                val index=(y*256+x)*4
                                val red=pixels.get(index).toInt() and 255;val green=pixels.get(index+1).toInt() and 255
                                if(red<235 && green>red+10) smokePixels++
                            }
                            assertTrue("Visible colored smoke outside card: $smokePixels",smokePixels>100)
                            assertTrue("Card center stays clear",(pixels.get((128*256+128)*4).toInt() and 255)>250)
                        } finally {
                            border.close();android.opengl.GLES20.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);android.opengl.GLES20.glClearColor(0f,0f,0f,1f)
                        }
                        assertEquals(android.opengl.GLES20.GL_NO_ERROR,android.opengl.GLES20.glGetError())
                    } catch(t:Throwable) { failure=t } finally { drawn.countDown() }
                }
            }
            assertTrue(drawn.await(10,TimeUnit.SECONDS));failure?.let { throw AssertionError(it) }
        }
        assertEquals(oldPreferences,c.getSharedPreferences("edscan_ar",0).all)
        assertEquals(before,io.asv.mtgocr.ocrreader.data.LegacyCollectionStore.cards(c).map { it.collectionItemId }.sorted())
    }
    @Test fun precompiledPackageStillFeedsFallbackAndDoesNotSave() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(PremiumAccess.isEnabled(c));assumeTrue(ArPackageStore.installedVersion(c,"FRA")!=null)
        assumeTrue(!(c.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
        ActivityScenario.launch<TripleScanActivity>(Intent(c,TripleScanActivity::class.java).putExtra("triple_set","FRA")).use { scenario ->
            waitFor(scenario) { (field(field(it,"preview")!!,"batchReadyCount") as Int)==574 }
            waitFor(scenario) { (field(it,"visualFrames") as Int)>0 }
            scenario.onActivity { assertEquals(574,(field(it,"references") as Map<*,*>).size) }
            scenario.onActivity { set(it,"lastFrameAt",SystemClock.elapsedRealtime()+60000) }
            waitFor(scenario) { !(field(it,"busy") as java.util.concurrent.atomic.AtomicBoolean).get() && !(field(it,"ocrScheduled") as java.util.concurrent.atomic.AtomicBoolean).get() }
            val local=java.io.File(c.cacheDir,"triple-append-smoke.jpg").apply {
                writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("triple-card.jpg").use { it.readBytes() })
            }
            scenario.onActivity { activity ->
                (field(activity,"preview") as TripleArPreview).addFallbackReference(ArPreloadCard("triple-extra-test",local.toURI().toString(),"Test","1 EUR"))
            }
            waitFor(scenario) { (field(field(it,"preview")!!,"batchReadyCount") as Int)==575 }
            scenario.onActivity { activity ->
                val cards=field(field(field(activity,"preview")!!,"camera")!!,"preloadedCards") as Map<*,*>
                assertEquals(575,cards.size)
                assertTrue(cards.containsKey("triple-extra-test"))
                assertTrue(cards.keys.containsAll((field(activity,"references") as Map<*,*>).keys))
            }

        }
    }
    @Test fun sessionReferencesAccumulateWithoutPackageAndSurvivePause() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val c=instrumentation.targetContext
        assumeTrue(!(c.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
        val local=java.io.File(c.cacheDir,"triple-session-smoke.jpg").apply { writeBytes(instrumentation.context.assets.open("triple-card.jpg").use { it.readBytes() }) }
        ActivityScenario.launch<TripleScanActivity>(Intent(c,TripleScanActivity::class.java).putExtra("triple_set","")).use { scenario ->
            waitFor(scenario) { (field(it,"visualFrames") as Int)>0 }
            scenario.onActivity { set(it,"lastFrameAt",SystemClock.elapsedRealtime()+60000) }
            waitFor(scenario) { !(field(it,"busy") as java.util.concurrent.atomic.AtomicBoolean).get() && !(field(it,"ocrScheduled") as java.util.concurrent.atomic.AtomicBoolean).get() }
            scenario.onActivity { activity ->
                val preview=field(activity,"preview") as TripleArPreview
                for(i in 1..5) preview.addFallbackReference(ArPreloadCard("session-$i",local.toURI().toString(),"Test","", "common"))
                preview.addFallbackReference(ArPreloadCard("session-1",local.toURI().toString(),"Test",""))
            }
            waitFor(scenario) { (field(field(it,"preview")!!,"batchReadyCount") as Int)==5 }
            scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertEquals(5,field(field(activity,"preview")!!,"batchReadyCount"))
                val camera=field(field(activity,"preview")!!,"camera")!!
                assertEquals(5,(field(camera,"preloadedCards") as Map<*,*>).size)
            }
        }
    }

    @Test fun completeSingleSetOcrCandidateFeedsArWithoutVisualMatch() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val c=instrumentation.targetContext
        assumeTrue(!(c.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
        val name="Triple synthetic OCR fixture"
        val local=java.io.File(c.cacheDir,"triple-ocr-route.jpg").apply { writeBytes(instrumentation.context.assets.open("triple-card.jpg").use { it.readBytes() }) }
        val key=java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
        val file=java.io.File(c.cacheDir,"triple-ocr-printings/$key.json")
        file.parentFile!!.mkdirs()
        val card=org.json.JSONObject().put("id","00000000-0000-0000-0000-000000000001").put("name",name).put("set","test").put("collector_number","1").put("rarity","common")
            .put("image_uris",org.json.JSONObject().put("normal",local.toURI().toString())).put("finishes",org.json.JSONArray().put("nonfoil"))
        file.writeText(org.json.JSONObject().put("has_more",false).put("data",org.json.JSONArray().put(card)).toString())
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(c,TripleScanActivity::class.java).putExtra("triple_set","")).use { scenario ->
                waitFor(scenario) { (field(it,"visualFrames") as Int)>0 }
                scenario.onActivity { set(it,"lastFrameAt",SystemClock.elapsedRealtime()+60000) }
                waitFor(scenario) { !(field(it,"busy") as java.util.concurrent.atomic.AtomicBoolean).get() && !(field(it,"ocrScheduled") as java.util.concurrent.atomic.AtomicBoolean).get() }
                scenario.onActivity { activity ->
                    TripleScanActivity::class.java.getDeclaredMethod("proposeOcrName",String::class.java).apply { isAccessible=true }.invoke(activity,name)
                    assertEquals("",(field(activity,"candidate") as TripleScanActivity.Target).printing)
                    assertEquals(1,(field(activity,"recent") as android.widget.LinearLayout).childCount)
                    assertEquals(1,field(activity,"feedbackCount"))
                }
                waitFor(scenario) { (field(it,"candidate") as? TripleScanActivity.Target)?.source=="OCR" }
                waitFor(scenario) { (field(field(it,"preview")!!,"batchReadyCount") as Int)==1 }
                scenario.onActivity {
                    assertEquals(name,(field(it,"candidate") as TripleScanActivity.Target).name)
                    assertEquals(1,(field(it,"recent") as android.widget.LinearLayout).childCount)
                    assertEquals(1,field(it,"feedbackCount"))
                    assertNull(field(it,"observedLanguage"))
                }
            }
        } finally { file.delete() }
    }

    @Test fun portugueseTitleIdentifiesLanguageWithoutArtOrEditionGate() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val c=instrumentation.targetContext
        assumeTrue(!(c.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
        val title="Madeixas da Medusa"
        val evidence=TitleLanguageIndex.read(c.assets.open(TitleLanguageIndex.ASSET)).lookup(listOf(title),listOf("Coils of the Medusa"))
        assertEquals(setOf("pt"),evidence.candidates)
        ActivityScenario.launch<TripleScanActivity>(Intent(c,TripleScanActivity::class.java).putExtra("triple_set","")).use { scenario ->
            scenario.onActivity { activity ->
                val root=activity.findViewById<android.view.View>(android.R.id.content)
                root.findViewWithTag<android.widget.CheckBox>("triple_method_ar").isChecked=false
                root.findViewWithTag<android.widget.CheckBox>("triple_method_art").isChecked=false
                set(activity,"lastFrameAt",SystemClock.elapsedRealtime()+60000)
            }
            waitFor(scenario) { !(field(it,"busy") as java.util.concurrent.atomic.AtomicBoolean).get() && !(field(it,"ocrScheduled") as java.util.concurrent.atomic.AtomicBoolean).get() }
            scenario.onActivity { activity ->
                val bitmap=android.graphics.Bitmap.createBitmap(630,182,android.graphics.Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE)
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.BLACK;textSize=40f }
                canvas.drawText(title,20f,100f,paint)
                TripleScanActivity::class.java.getDeclaredMethod("guidedOcr",android.graphics.Bitmap::class.java,Int::class.javaPrimitiveType).apply { isAccessible=true }
                    .invoke(activity,bitmap,field(activity,"generation"))
            }
            waitFor(scenario) { field(it,"observedLanguage")=="pt" }
            scenario.onActivity { activity ->
                assertEquals("Coils of the Medusa",(field(activity,"candidate") as TripleScanActivity.Target).name)
                val gallery=field(activity,"recent") as android.widget.LinearLayout
                assertEquals(1,gallery.childCount)
                assertEquals(CardLanguageFlag.label("pt"),gallery.getChildAt(0).findViewWithTag<android.widget.TextView>("triple_gallery_language").text.toString())
                assertEquals(1,field(activity,"feedbackCount"))
            }
        }
    }

}
