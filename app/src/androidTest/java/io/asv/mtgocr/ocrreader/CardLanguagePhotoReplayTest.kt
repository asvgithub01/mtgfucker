package io.asv.mtgocr.ocrreader

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in local Sony photo corpus on emulator ONLY; never writes any collection. */
class CardLanguagePhotoReplayTest {
    @Test fun replaySavedPhotos() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val inst=InstrumentationRegistry.getInstrumentation()
        val dir=InstrumentationRegistry.getArguments().getString("languageReplay") ?: return
        val fixtures=JSONArray(File(dir,"manifest.json").readText())
        val index=TitleLanguageIndex.read(inst.targetContext.assets.open(TitleLanguageIndex.ASSET))
        val reader=TriplePhotoLanguageReader({index},{})
        val oldOcr=TripleSnapshotOcr();val detector=CardTextLanguageDetector(useShortRules=false)
        val reports=JSONArray()
        try {
            for(i in 0 until fixtures.length()) {
                val fixture=fixtures.getJSONObject(i)
                val bytes=File(dir,fixture.getString("file")).readBytes()
                val identity=fixture.getString("identity")
                val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                val recognized=try { Tasks.await(oldOcr.process(InputImage.fromBitmap(bitmap,0)),60,TimeUnit.SECONDS) } finally { bitmap.recycle() }
                val lines=recognized.textBlocks.flatMap { it.lines }
                val titles=lines.filter { (it.boundingBox?.centerY() ?: 9999)<170 }.map { it.text }
                val rules=lines.filter { (it.boundingBox?.centerY() ?: 0) in 510..800 }.joinToString(" ") { it.text }
                val title=index.lookup(titles,listOf(identity))
                val latch=CountDownLatch(1);var statistical:String?=null
                detector.detectText(rules,"und") { statistical=TripleScanPolicy.language(it.languageCode,it.confidence);latch.countDown() }
                assertTrue(latch.await(30,TimeUnit.SECONDS))
                val oldDecision=CardLanguageResolver.resolve(rules,statistical,title)
                val old=oldDecision.code?.takeIf { !oldDecision.conflict && !title.conflict && it in title.candidates }
                val done=CountDownLatch(1);var result:TriplePhotoLanguageReader.Report?=null
                Handler(Looper.getMainLooper()).post { reader.submitReport(bytes,identity) { result=it;done.countDown() } }
                assertTrue(done.await(90,TimeUnit.SECONDS))
                val report=result!!.json().put("fixture",fixture).put("oldLanguage",old.orEmpty())
                reports.put(report)
                File(inst.targetContext.cacheDir,"language-replay-results.json").writeText(reports.toString(2))
                if(InstrumentationRegistry.getArguments().getString("assertReplay")=="true")
                    assertEquals(fixture.getString("file"),fixture.optString("expected"),result!!.decision.code.orEmpty())
            }
        } finally { reader.close();oldOcr.close();detector.close() }
    }
}
