package io.asv.mtgocr.ocrreader

import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit emulator-only private corpus; no database/session writer or physical-device access. */
@RunWith(AndroidJUnit4::class)
class EdScanFastRegionReplayTest {
    @Test fun completeIndexAndRealSonyStressPhotos() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Emulator only" }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"edfast-region-replay")
        org.junit.Assume.assumeTrue("Explicit private fixtures required",File(folder,"expected.json").isFile)
        val expected=JSONObject(File(folder,"expected.json").readText())
        val index=FastRegionIndex(context)
        assertTrue("Do not silently test a partial index",index.available)
        assertEquals(110278,index.rows.size)
        val report=JSONArray()
        NativeCameraVisionEngine.prepareForCamera(context).use { engine ->
            for(profile in FastRegion.entries) {
                engine.configureFast(index,profile)
                for(file in folder.listFiles().orEmpty().filter { it.extension=="jpg" }.sortedBy { it.name }) {
                    val raw=BitmapFactory.decodeFile(file.path)
                    val fullArt=file.name=="32f7b1e9-cd24-4028-b6ea-1a0fdac3f6c0.jpg"
                    // Saved full-art photo is already rectified: add neutral surroundings so
                    // Cornelius can detect its border. This is an augmented real photo, not a raw frame.
                    val bitmap=if(fullArt) android.graphics.Bitmap.createBitmap(720,960,android.graphics.Bitmap.Config.ARGB_8888).also {
                        val canvas=android.graphics.Canvas(it);canvas.drawColor(android.graphics.Color.GRAY)
                        canvas.drawBitmap(raw,null,android.graphics.Rect(110,130,610,830),android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                        raw.recycle()
                    } else raw
                    try {
                        val scan=engine.scan(bitmap)
                        val evidence=EdScanFastIdentity.resolve(scan.hits.map { h ->
                            val row=index.byId.getValue(h.cardId)
                            EdScanFastIdentity.Candidate(h.cardId,h.score,row.identity,row.set,row.released)
                        })
                        val policy=EdScanFastPolicy()
                        policy.observe(scan.present,evidence.identity,evidence.score,evidence.runner,1000,1900)
                        val accepted=policy.observe(scan.present,evidence.identity,evidence.score,evidence.runner,2100,3000).confirmed
                        report.put(JSONObject().put("file",file.name).put("region",profile.key).put("identity",evidence.identity)
                            .put("strong",accepted).put("score",evidence.score).put("runner",evidence.runner)
                            .put("conflict",scan.regionConflict).put("ms",scan.recognitionMs).put("lanes",scan.regionDiagnostics).put("augmentedBorder",fullArt))
                        File(folder,"results.json").writeText(report.toString(2))
                        if(accepted) assertEquals("No strong wrong identity ${file.name}/${profile.key}",expected.getString(file.name),evidence.identity)
                        if(file.name=="1792148465485.jpg" && profile==FastRegion.FULL) {
                            assertEquals("lord of the pit",evidence.identity)
                            assertNotNull("Top-eight duplicate prints must not hide rival",evidence.runner)
                        }
                        if(file.name=="1792148312327.jpg" && profile==FastRegion.ART) assertTrue("Real Korean Mummy with global competitors",accepted)
                        if(file.name=="1792157079658.jpg" && profile!=FastRegion.FULL) assertTrue("Bartolome showcase now participates in crop index",accepted)
                        if(fullArt) assertTrue("Full-art keeps its complete-card evidence in every mode",accepted)
                    } finally { bitmap.recycle() }
                }
            }
        }
        assertEquals(18,report.length())
    }
}
