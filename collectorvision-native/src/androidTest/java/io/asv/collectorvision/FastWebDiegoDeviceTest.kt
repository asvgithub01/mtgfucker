package io.asv.collectorvision
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
/** Isolated library test APK: never starts host or accesses the user's collection. */
class FastWebDiegoDeviceTest {
 @Test fun frozenSonyCapturesPreserveCornersAndTopFive() {
  val inst=InstrumentationRegistry.getInstrumentation()
  val fixtures=JSONArray(inst.context.assets.open("expected.json").bufferedReader().use { it.readText() })
  FastWebDiegoEngine.prepare(inst.targetContext).use { engine ->
   for(i in 0 until fixtures.length()) {
    val row=fixtures.getJSONObject(i)
    val bitmap=inst.context.assets.open(row.getString("file")).use { BitmapFactory.decodeStream(it) }!!
    try {
     var contourCalled=false
     var inputCalled=false
     val result=engine.scan(bitmap,onDetected={ detection ->
      contourCalled=true; assertTrue(detection.hits.isEmpty()); assertEquals(0L,detection.recognitionMs)
     },onInput={ crop,rgb,tensor ->
      assertTrue(contourCalled);inputCalled=true
      assertEquals(640,crop.width);assertEquals(896,crop.height)
      assertEquals(320*448*3,rgb.size);assertEquals(rgb.size,tensor.size)
      assertTrue(tensor.all { it.isFinite() })
     })
     assertTrue(contourCalled)
     assertTrue(inputCalled)
     assertTrue("presence $i",result.present)
     val q=row.getJSONArray("quads").getJSONArray(0)
     for(k in 0..3) {
      assertEquals("x $i/$k",q.getJSONArray(k).getDouble(0),result.corners[k].x.toDouble(),1e-5)
      assertEquals("y $i/$k",q.getJSONArray(k).getDouble(1),result.corners[k].y.toDouble(),1e-5)
     }
     val top=row.getJSONArray("top")
     for(k in 0 until top.length()) {
      assertEquals("rank $i/$k",top.getJSONObject(k).getString("id"),result.hits[k].cardId)
      assertEquals("score $i/$k",top.getJSONObject(k).getDouble("cosine"),result.hits[k].score.toDouble(),.002)
     }
     assertEquals(0,result.photoRotation)
     if(i==0) {
      val allowed=result.hits.drop(5).map { it.cardId }.toSet()
      val scoped=engine.scan(bitmap,allowed)
      assertEquals(allowed,scoped.hits.map { it.cardId }.toSet())
      assertEquals(allowed.size,scoped.searchedRows)
      assertTrue(engine.scan(bitmap,emptySet()).hits.isEmpty())
      assertEquals(result.hits.first().cardId,engine.scan(bitmap).hits.first().cardId)
     }
    } finally { bitmap.recycle() }
   }
  }
 }
}
