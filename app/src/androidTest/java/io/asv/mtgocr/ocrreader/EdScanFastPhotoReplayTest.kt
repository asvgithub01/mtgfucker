package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.NativeCollectorVisionEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Opt-in private Sony fixtures in cache/edfast-replay, NEVER in APK/Git or physical devices. */
@RunWith(AndroidJUnit4::class)
class EdScanFastPhotoReplayTest {
    @Test fun replayPrivatePhotosAtOriginalAndBoundedResolution() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Emulator only" }
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(ctx.cacheDir,"edfast-replay")
        org.junit.Assume.assumeTrue("Push private replay fixtures explicitly",File(dir,"names.json").isFile)
        val names=JSONObject(File(dir,"names.json").readText())
        val report=JSONArray()
        NativeCollectorVisionEngine.prepareForCamera(ctx).use { engine ->
            for(file in dir.listFiles().orEmpty().filter { it.extension=="jpg" }.sortedBy { it.name }) {
                val original=BitmapFactory.decodeFile(file.path)
                try { for(limit in listOf(2160,1280)) {
                    val scale=minOf(1f,limit.toFloat()/maxOf(original.width,original.height))
                    val bitmap=Bitmap.createScaledBitmap(original,(original.width*scale).toInt(),(original.height*scale).toInt(),true)
                    try {
                        val scan=engine.scan(bitmap)
                        val candidates=scan.hits.map { h -> val m=names.optJSONArray(h.cardId)
                            EdScanFastIdentity.Candidate(h.cardId,h.score,m?.getString(0)?.lowercase(Locale.ROOT),m?.getString(1).orEmpty()) }
                        val e=EdScanFastIdentity.resolve(candidates)
                        val p=EdScanFastPolicy()
                        p.observe(scan.present,e.identity,e.score,e.runner,1000,1900)
                        val accepted=p.observe(scan.present,e.identity,e.score,e.runner,2100,3000).confirmed
                        report.put(JSONObject().put("file",file.name).put("width",bitmap.width).put("present",scan.present)
                            .put("accepted",accepted).put("identity",e.identity).put("score",e.score).put("runner",e.runner)
                            .put("reference",e.printing).put("hits",JSONArray(scan.hits.map { "${it.cardId}:${it.score}" })))
                        File(dir,"results.json").writeText(report.toString(2))
                        if(file.name=="1792143449797.jpg") {
                            assertEquals("belligerent brontodon",e.identity);assertTrue("Brontodon at $limit",accepted)
                            assertEquals("xln",names.getJSONArray(requireNotNull(e.printing)).getString(1))
                        } else assertFalse("No relaxing low-score/empty photos: ${file.name} at $limit",accepted)
                    } finally { if(bitmap!==original) bitmap.recycle() }
                } } finally { original.recycle() }
            }
            assertEquals("Five private photos at two resolutions",10,report.length())
        }
    }
}
