package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CardFaceToggleTest {
    private val test=InstrumentationRegistry.getInstrumentation()
    private val context get()=test.targetContext
    private fun emulator()=check(Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
    private fun meta(id:String)=JSONObject("""{"id":"$id","card_faces":[{"image_uris":{"normal":"https://cards.scryfall.io/normal/front/a/a/$id.jpg"}},{"image_uris":{"normal":"https://cards.scryfall.io/normal/back/a/a/$id.jpg"}}]}""")
    @Test fun fullscreenSony313FlipsAndChangingPageHidesButton() {
        emulator()
        val json=JSONObject(test.context.assets.open("lci313-faces.json").bufferedReader().use { it.readText() })
        val pair=requireNotNull(CardFaceImages.fromJson(json));val id=json.getString("id")
        val single=java.util.UUID.randomUUID().toString()
        val dir=File(context.cacheDir,"card-face-meta").apply { mkdirs() }
        val files=listOf(File(dir,"$id.json"),File(dir,"$single.json"))
        val originals=files.map { if(it.isFile) it.readBytes() else null }
        files[0].writeText(json.toString());files[1].writeText(JSONObject().put("id",single).put("image_uris",JSONObject().put("normal","single")).toString())
        try {
            val intent=Intent(context,CardImageActivity::class.java)
                .putStringArrayListExtra(CardImageActivity.EXTRA_EDITION_IMAGE_URLS,arrayListOf(pair.front,"https://cards.scryfall.io/normal/front/a/a/$single.jpg"))
                .putStringArrayListExtra(CardImageActivity.EXTRA_EDITION_LABELS,arrayListOf("LCI313","Single"))
                .putStringArrayListExtra(CardImageActivity.EXTRA_EDITION_SET_CODES,arrayListOf("",""))
                .putStringArrayListExtra(CardImageActivity.EXTRA_EDITION_COLLECTOR_NUMBERS,arrayListOf("",""))
            ActivityScenario.launch<CardImageActivity>(intent).use { scene ->
                lateinit var button:ImageButton
                scene.onActivity { button=it.findViewById(R.id.btnGalleryFlip) }
                repeat(50) { if(button.visibility!=View.VISIBLE) Thread.sleep(100) }
                scene.onActivity { a ->
                    assertEquals(View.VISIBLE,button.visibility)
                    val image=CardImageActivity::class.java.getDeclaredField("image").apply { isAccessible=true }.get(a) as ImageView
                    button.performClick();assertEquals(pair.back,image.getTag(R.id.card_image_cache_url))
                    button.performClick();assertEquals(pair.front,image.getTag(R.id.card_image_cache_url))
                    a.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.galleryPager).setCurrentItem(1,false)
                }
                Thread.sleep(700)
                scene.onActivity { assertEquals(View.GONE,button.visibility) }
            }
        } finally { files.forEachIndexed { i,f -> originals[i]?.let { f.writeBytes(it) } ?: f.delete() } }
    }

    @Test fun onlyPhysicalDoubleFacesAndStrictImageIds() {
        val id=java.util.UUID.randomUUID().toString();val json=meta(id)
        assertNotNull(CardFaceImages.fromJson(json))
        assertNull(CardFaceImages.fromJson(meta(id).put("image_uris",JSONObject().put("normal","split"))))
        assertNull(CardFaceImages.fromJson(JSONObject("""{"card_faces":[{"name":"a"},{"name":"b"}]}""")))
        assertEquals(id,CardFaceImages.imageId("https://cards.scryfall.io/normal/back/a/a/$id.jpg?123"))
        assertNull(CardFaceImages.imageId("https://other.example/$id.jpg"))
    }
    @Test fun galleryAndDetailFlipImagesWithoutMutatingTargetOrFoil() {
        emulator()
        val id=java.util.UUID.randomUUID().toString();val json=meta(id);val pair=CardFaceImages.fromJson(json)!!
        val file=File(context.cacheDir,"card-face-meta/$id.json").apply { parentFile?.mkdirs();writeText(json.toString()) }
        val prefs=context.getSharedPreferences("triple_scanner",0)
        val modes=listOf("ar","art","ocr").associateWith { prefs.getBoolean(it,true) }
        prefs.edit().putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false).commit()
        fun field(name:String)=TripleScanActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(context,TripleScanActivity::class.java).putExtra("triple_diagnostic_only",true)).use { scene ->
                val target=TripleScanActivity.Target(id,"face-test","Double","LCI","Double #313",pair.front,"foil","EdScan")
                lateinit var button:ImageButton;lateinit var image:ImageView
                scene.onActivity { a ->
                    TripleScanActivity::class.java.getDeclaredMethod("upsertGallery",TripleScanActivity.Target::class.java).apply { isAccessible=true }.invoke(a,target)
                    val column=(field("recent").get(a) as LinearLayout).getChildAt(0)
                    button=column.findViewWithTag("triple_gallery_flip")
                    val entries=field("galleryEntries").get(a) as Map<*,*>
                    val entry=entries.values.single()!!
                    image=entry.javaClass.getDeclaredField("image").apply { isAccessible=true }.get(entry) as ImageView
                }
                repeat(40) { if(button.visibility!=View.VISIBLE) Thread.sleep(100) }
                scene.onActivity { a ->
                    assertEquals(View.VISIBLE,button.visibility)
                    assertEquals(a.getString(R.string.card_show_back),button.contentDescription)
                    button.performClick();assertEquals(pair.back,image.getTag(R.id.card_image_cache_url))
                    assertEquals(a.getString(R.string.card_show_front),button.contentDescription)
                    val entries=field("galleryEntries").get(a) as Map<*,*>;val entry=entries.values.single()!!
                    assertEquals(target,entry.javaClass.getDeclaredField("target").apply { isAccessible=true }.get(entry))
                    assertEquals("nonfoil",entry.javaClass.getDeclaredField("selectedFinish").apply { isAccessible=true }.get(entry))
                    assertEquals(1,entry.javaClass.getDeclaredField("count").apply { isAccessible=true }.getInt(entry))
                    button.performClick();assertEquals(pair.front,image.getTag(R.id.card_image_cache_url))
                    // Rebinding same printing does not undo a manually selected visual face.
                    button.performClick()
                    TripleScanActivity::class.java.getDeclaredMethod("upsertGallery",TripleScanActivity.Target::class.java).apply { isAccessible=true }.invoke(a,target)
                    assertEquals(pair.back,image.getTag(R.id.card_image_cache_url))
                }
            }
            ActivityScenario.launch<Main2Activity>(Intent(context,Main2Activity::class.java)
                .putExtra("browseOnly",true).putExtra(Main2Activity.EXTRA_CARD_NAME,"Face toggle fixture")).use { scene ->
                lateinit var button:ImageButton
                scene.onActivity { a ->
                    Main2Activity::class.java.getDeclaredMethod("showDetailImage",String::class.java).apply { isAccessible=true }.invoke(a,pair.front)
                    button=a.findViewById(R.id.btnDetailFlip)
                }
                repeat(40) { if(button.visibility!=View.VISIBLE) Thread.sleep(100) }
                scene.onActivity { a ->
                    assertEquals(View.VISIBLE,button.visibility);button.performClick()
                    assertEquals(pair.back,a.findViewById<ImageView>(R.id.imgEditionDetail).getTag(R.id.card_image_cache_url))
                    button.performClick();assertEquals(pair.front,a.findViewById<ImageView>(R.id.imgEditionDetail).getTag(R.id.card_image_cache_url))
                }
            }
        } finally { file.delete();prefs.edit().also { e -> modes.forEach { (k,v)->e.putBoolean(k,v) } }.commit() }
    }
    @Test fun staleMetadataCannotRestoreButtonAfterRebindOrClose() {
        emulator()
        val id=java.util.UUID.randomUUID().toString();val pair=CardFaceImages.fromJson(meta(id))!!
        val file=File(context.cacheDir,"card-face-meta/$id.json").apply { parentFile?.mkdirs();writeText(meta(id).toString()) }
        lateinit var button:ImageButton;lateinit var toggle:CardFaceToggle
        var shown=""
        try {
            test.runOnMainSync {
                button=ImageButton(context);toggle=CardFaceToggle(button) { shown=it }
                toggle.bind(pair.front,id);toggle.bind("","")
            }
            Thread.sleep(500)
            test.runOnMainSync {
                assertEquals(View.GONE,button.visibility);assertEquals("",shown)
                toggle.bind(pair.front,id);toggle.close()
            }
            Thread.sleep(500)
            test.runOnMainSync { assertEquals(View.GONE,button.visibility);assertEquals(pair.front,shown) }
        } finally { file.delete() }
    }
}
