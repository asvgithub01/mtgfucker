package io.asv.mtgocr.ocrreader

import android.graphics.*
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real native inference replay, isolated from collection writers. Not mobile latency evidence. */
@RunWith(AndroidJUnit4::class)
class EdScanFastAutoReplayTest {
    @Test fun hybridRescuesMummyAndShowcaseWhileAlwaysKeepingFullArt() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"edfast-region-replay")
        org.junit.Assume.assumeTrue(File(folder,"expected.json").isFile)
        val expected=JSONObject(File(folder,"expected.json").readText())
        val index=FastRegionIndex(context)
        val report=JSONArray()
        val files=folder.listFiles().orEmpty().filter { it.extension=="jpg" }.sortedBy { it.name }
        assertEquals(6,files.size)
        NativeCameraVisionEngine.prepareForCamera(context).use { engine ->
            engine.configureFast(index,FastRegion.UPPER) // preload rescue outside camera timing
            for(file in files) {
                val auto=EdScanFastAutoPolicy();val confidence=EdScanFastPolicy()
                val raw=BitmapFactory.decodeFile(file.path)
                val fullArt=file.name.startsWith("32f7b1e9")
                val bitmap=if(fullArt) Bitmap.createBitmap(720,960,Bitmap.Config.ARGB_8888).also {
                    val canvas=Canvas(it);canvas.drawColor(Color.GRAY)
                    canvas.drawBitmap(raw,null,Rect(110,130,610,830),Paint(Paint.FILTER_BITMAP_FLAG));raw.recycle()
                } else raw
                var accepted=false
                val profiles=mutableListOf<String>()
                try {
                    for(attempt in 0 until 6) {
                        val profile=auto.region;profiles.add(profile.key)
                        engine.configureFast(index,profile)
                        val start=SystemClock.elapsedRealtime()
                        val scan=engine.scan(bitmap)
                        val evidence=EdScanFastIdentity.resolve(scan.hits.map { h ->
                            val row=index.byId.getValue(h.cardId)
                            EdScanFastIdentity.Candidate(h.cardId,h.score,row.identity,row.set,row.released)
                        })
                        val reading=confidence.observe(scan.present,evidence.identity,evidence.score,evidence.runner,start,SystemClock.elapsedRealtime())
                        report.put(JSONObject().put("file",file.name).put("profile",profile.key).put("reason",reading.reason)
                            .put("identity",evidence.identity).put("score",evidence.score).put("runner",evidence.runner)
                            .put("ms",scan.recognitionMs).put("lanes",scan.regionDiagnostics))
                        File(folder,"auto-results.json").writeText(report.toString(2))
                        if(profile!=FastRegion.FULL) assertFalse("Whole lane must always run",scan.regionDiagnostics.contains("fullTop=null"))
                        if(auto.observe(scan.present,reading)) confidence.reset()
                        if(reading.confirmed) {
                            assertEquals(expected.getString(file.name),evidence.identity)
                            accepted=true;break
                        }
                    }
                    assertTrue("Must confirm ${file.name} within six independent inference calls",accepted)

                    assertEquals(listOf("art","art"),profiles)
                    auto.observe(false,EdScanFastPolicy.Reading(false,"no_detection",0))
                    assertEquals(FastRegion.ART,auto.region)
                } finally { bitmap.recycle() }
            }
        }
    }
    @Test fun realCardChangesWithoutEmptyFrameReturnToFullAndRescueUpper() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"edfast-cycle-replay")
        org.junit.Assume.assumeTrue(File(folder,"mountain.jpg").isFile)
        val index=FastRegionIndex(context)
        val auto=EdScanFastAutoPolicy();val confidence=EdScanFastPolicy()
        val report=JSONArray()
        NativeCameraVisionEngine.prepareForCamera(context).use { engine ->
            engine.configureFast(index,FastRegion.ART);engine.configureFast(index,FastRegion.UPPER)
            val cases=listOf(
                Triple(File(context.cacheDir,"edfast-region-replay/1792148312327.jpg"),"cyclopean mummy",listOf("art","art")),
                Triple(File(folder,"mountain.jpg"),"mountain",listOf("art","art")),
                Triple(File(folder,"monstrosaur.jpg"),"charging monstrosaur",listOf("art","art","upper","upper"))
            )
            // Keep BOTH scheduler and confidence between cards: no fake no_detection/reset.
            for((file,identity,expectedProfiles) in cases) {
                val bitmap=BitmapFactory.decodeFile(file.path)
                val profiles=mutableListOf<String>();var accepted=false
                try {
                    repeat(6) {
                        if(!accepted) {
                            val profile=auto.region;profiles.add(profile.key)
                            engine.configureFast(index,profile)
                            val start=SystemClock.elapsedRealtime()
                            val scan=engine.scan(bitmap)
                            val evidence=EdScanFastIdentity.resolve(scan.hits.map { h ->
                                val row=index.byId.getValue(h.cardId)
                                EdScanFastIdentity.Candidate(h.cardId,h.score,row.identity,row.set,row.released)
                            })
                            val r=confidence.observe(scan.present,evidence.identity,evidence.score,evidence.runner,start,SystemClock.elapsedRealtime())
                            report.put(JSONObject().put("file",file.name).put("profile",profile.key).put("identity",evidence.identity)
                                .put("score",evidence.score).put("runner",evidence.runner).put("reason",r.reason))
                            File(folder,"results.json").writeText(report.toString(2))
                            assertTrue("Real photo must be detected",scan.present)
                            if(auto.observe(scan.present,r)) confidence.reset()
                            if(r.confirmed) { assertEquals(identity,evidence.identity);accepted=true }
                        }
                    }
                    assertTrue("Must recover $identity without withdrawing card",accepted)
                    assertEquals(expectedProfiles,profiles)
                } finally { bitmap.recycle() }
            }
        }
    }

}
