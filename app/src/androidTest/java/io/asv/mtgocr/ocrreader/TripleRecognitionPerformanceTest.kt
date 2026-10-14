package io.asv.mtgocr.ocrreader

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.NativeCollectorVisionEngine
import io.asv.mtgocr.ocrreader.data.CardRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only replay: no Activity, camera, inventory writes or preference changes. */
@RunWith(AndroidJUnit4::class)
class TripleRecognitionPerformanceTest {
    @Test fun compareGlobalAndSelectedEditionWithoutChangingInventory() {
        val test=InstrumentationRegistry.getInstrumentation()
        val context=test.targetContext
        val meta=JSONObject(test.context.assets.open("triple-card.json").bufferedReader().use { it.readText() })
            .getJSONArray("references").getJSONObject(0)
        val card=BitmapFactory.decodeStream(test.context.assets.open("triple-card.jpg"))
        var scope=emptySet<String>();var failure:Throwable?=null
        val loaded=CountDownLatch(1)
        CardRepository.get(context).loadVisualSetIds(setOf(meta.getString("setCode"))) { ids,error ->
            scope=ids;failure=error;loaded.countDown()
        }
        assertTrue(loaded.await(90,TimeUnit.SECONDS));assertNull(failure)
        assertTrue(scope.contains(meta.getString("scryfallId")))
        val report=JSONArray()
        try {
            NativeCollectorVisionEngine.prepare(context).use { engine ->
                engine.scan(card,scope) // warm-up excluded
                for(selected in listOf(false,true)) {
                    val confirmation=TripleVisualConfirmation()
                    val started=SystemClock.elapsedRealtime()
                    repeat(4) {
                        val at=SystemClock.elapsedRealtime()
                        val scan=engine.scan(card,if(selected) scope else null)
                        val top=scan.hits.firstOrNull()
                        val result=confirmation.observe(top?.cardId,top?.score,scan.hits.getOrNull(1)?.score,at)
                        if(selected) assertTrue(scan.hits.all { it.cardId in scope })
                        assertTrue(scan.present)
                        report.put(JSONObject().put("selected",selected).put("frame",it)
                            .put("totalMs",SystemClock.elapsedRealtime()-at).put("detectMs",scan.detectionMs)
                            .put("inferMs",scan.recognitionMs).put("searchMs",scan.searchMs).put("rows",scan.searchedRows)
                            .put("score",top?.score).put("runner",scan.hits.getOrNull(1)?.score)
                            .put("reason",result.reason).put("sinceStartMs",SystemClock.elapsedRealtime()-started)
                            .put("expectedTop",top?.cardId==meta.getString("scryfallId")))
                    }
                }
                assertTrue(engine.scan(card,emptySet()).hits.isEmpty()) // empty scope never expands globally
            }
            NativeCollectorVisionEngine.prepareForCamera(context).use { engine ->
                engine.scan(card,scope)
                repeat(16) { index ->
                    val started=SystemClock.elapsedRealtime()
                    val scan=engine.scan(card,scope)
                    assertEquals(meta.getString("scryfallId"),scan.hits.first().cardId)
                    assertTrue(scan.hits.first().score>=TripleScanPolicy.VISUAL_MIN_SCORE)
                    report.put(JSONObject().put("cameraFriendly",true).put("frame",index)
                        .put("totalMs",SystemClock.elapsedRealtime()-started).put("detectMs",scan.detectionMs)
                        .put("inferMs",scan.recognitionMs).put("searchMs",scan.searchMs)
                        .put("score",scan.hits.first().score))
                    Thread.sleep(150)
                }
            }
        } finally { card.recycle() }
        test.sendStatus(0,Bundle().apply { putString("stream","TRIPLE_BENCHMARK $report\n") })
    }
}
