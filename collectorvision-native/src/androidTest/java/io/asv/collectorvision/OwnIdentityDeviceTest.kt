package io.asv.collectorvision

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** PC/Android contract test, not an accuracy estimate. Isolated library test APK only. */
class OwnIdentityDeviceTest {
    @Test fun optimizedResizeMatchesReferenceExactly() {
        val random=java.util.Random(20261027)
        val records=org.json.JSONArray()
        for((w,h) in listOf(640 to 896,320 to 448,713 to 997,91 to 127,1 to 1)) {
            val pixels=IntArray(w*h) { random.nextInt() }
            assertArrayEquals(OwnIdentityPreprocess.viewReference(pixels,w,h),OwnIdentityPreprocess.view(pixels,w,h))
            if(w==640) {
                repeat(3) { OwnIdentityPreprocess.viewReference(pixels,w,h);OwnIdentityPreprocess.view(pixels,w,h) }
                repeat(12) { n ->
                    // Alternate order to limit warm-up/thermal ordering bias.
                    for(reference in if(n%2==0) listOf(true,false) else listOf(false,true)) {
                        val start=System.nanoTime()
                        if(reference) OwnIdentityPreprocess.viewReference(pixels,w,h) else OwnIdentityPreprocess.view(pixels,w,h)
                        records.put(JSONObject().put("reference",reference).put("ms",(System.nanoTime()-start)/1e6))
                    }
                }
            }
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.filesDir,"odin-resize-ab.json").writeText(records.toString(2))
    }
    @Test fun sameFramesCornersAndSearchScoresAsPc() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val expected=JSONObject(inst.context.assets.open("own/expected.json").bufferedReader().use { it.readText() })
        val packaged=JSONObject(inst.targetContext.assets.open("ownidentity/manifest.json").bufferedReader().use { it.readText() })
        val hashes=expected.getJSONObject("model_manifest")
        for(key in hashes.keys()) assertEquals(hashes.getString(key),packaged.getString(key))
        val rows=expected.getJSONArray("rows")
        val timingRows = org.json.JSONArray()
        OwnIdentityEngine.prepare(inst.targetContext).use { engine ->
            for(i in 0 until rows.length()) {
                val row=rows.getJSONObject(i)
                val bitmap=inst.context.assets.open(row.getString("file")).use { BitmapFactory.decodeStream(it) }!!
                try {
                    var callback=false
                    val result=engine.scan(bitmap,onInput={crop,rgb,data ->
                        callback=true;assertEquals(640,crop.width);assertEquals(896,crop.height)
                        assertEquals(320*448*3,rgb.size);assertEquals(rgb.size,data.size)
                        assertTrue(data.all { it.isFinite() })
                    })
                    assertTrue(callback);assertTrue(result.present);assertEquals(expected.getInt("gallery_rows"),result.searchedRows);assertEquals(result.searchedRows,engine.identityRows.size)
                    timingRows.put(JSONObject(engine.lastTimings).put("fixture",i))
                    val quad=row.getJSONArray("quad")
                    for(k in 0..3) {
                        assertEquals(quad.getJSONArray(k).getDouble(0),result.corners[k].x.toDouble(),1e-5)
                        assertEquals(quad.getJSONArray(k).getDouble(1),result.corners[k].y.toDouble(),1e-5)
                    }
                    // Full catalog must supply a competing oracle even if TopK8 is all reprints.
                    assertTrue("missing distinct-identity runner on fixture $i",
                        result.hits.map { engine.identityRows.getValue(it.cardId).identity }.distinct().size >= 2)
                    val top=row.getJSONArray("top")
                    val reference=(0 until top.length()).associate { j -> top.getJSONObject(j).let { it.getString("id") to it.getDouble("score") } }
                    val best=reference.values.maxOrNull()!!
                    assertEquals(best,result.hits.first().score.toDouble(),.002)
                    // Identical-art printings can tie; compare score and admissible top IDs rather than tie ordering.
                    val acceptable=row.getJSONArray("acceptable_ids").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                    assertTrue("top1 differs from PC on fixture $i",result.hits.first().cardId in acceptable)
                    val allowed=reference.keys.take(3).toSet()
                    val scoped=engine.scan(bitmap,allowed)
                    assertEquals(allowed,scoped.hits.map { it.cardId }.toSet())
                    for(hit in scoped.hits) assertEquals(reference.getValue(hit.cardId),hit.score.toDouble(),.002)
                    assertTrue(engine.scan(bitmap,emptySet()).hits.isEmpty())
                } finally { bitmap.recycle() }
            }
        }
        java.io.File(inst.targetContext.filesDir,"odin-stage-timings.json").writeText(timingRows.toString(2))
    }
}
