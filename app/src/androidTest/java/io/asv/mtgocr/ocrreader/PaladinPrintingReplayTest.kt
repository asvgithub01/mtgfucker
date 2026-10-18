package io.asv.mtgocr.ocrreader
import android.graphics.*
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class PaladinPrintingReplayTest {
    @Test fun sonyPaladinPhotosPreferOrdinaryPrintingWithoutChangingEvidence() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val dir=InstrumentationRegistry.getArguments().getString("paladinReplay") ?: return
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val index=FastRegionIndex(ctx)
        val output=JSONArray()
        NativeCollectorVisionEngine.prepareForCamera(ctx).use { engine ->
            engine.configureFast(index,FastRegion.UPPER)
            for(name in listOf("24.jpg","27.jpg")) {
                val raw=BitmapFactory.decodeFile(File(dir,name).path)
                val bitmap=Bitmap.createBitmap(800,1100,Bitmap.Config.ARGB_8888)
                Canvas(bitmap).apply { drawColor(Color.GRAY);drawBitmap(raw,null,Rect(85,110,715,990),Paint(Paint.FILTER_BITMAP_FLAG)) }
                val scan=try { engine.scan(bitmap) } finally { raw.recycle();bitmap.recycle() }
                val candidates=scan.hits.map { h -> val r=index.byId.getValue(h.cardId)
                    EdScanFastIdentity.Candidate(h.cardId,h.score,r.identity,r.set,r.released) }
                val e=EdScanFastIdentity.resolve(candidates)
                val old=candidates.filter { it.identity==e.identity && e.score!!-it.score<TripleScanPolicy.VISUAL_MIN_MARGIN }
                    .sortedWith(compareBy<EdScanFastIdentity.Candidate> { it.set=="plst" }.thenByDescending { it.released }).first()
                val selected=candidates.first { it.id==e.printing }
                output.put(JSONObject().put("file",name).put("old",old.set).put("new",selected.set)
                    .put("score",e.score).put("runner",e.runner).put("candidates",JSONArray(candidates.map { "${it.set}:${it.score}" })))
                File(ctx.cacheDir,"paladin-replay-results.json").writeText(output.toString(2))
                assertTrue(scan.present);assertEquals("northern paladin",e.identity)
                assertTrue(e.score!!>=TripleScanPolicy.VISUAL_MIN_SCORE)
                assertTrue(e.score!!-e.runner!!>=TripleScanPolicy.VISUAL_MIN_MARGIN)
                assertFalse(selected.set in setOf("30a","ced","cei","lea","leb","sum","plst"))
                assertEquals(candidates.maxOf { it.score },e.score!!,0f)
            }
        }
    }
}
