package io.asv.mtgocr.ocrreader
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.*
import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CollectorCoverageDeviceTest {
 @Test fun reportsNewCollectorReferenceRankingsWithoutSavingCards() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val owner=Any(); EdScanInferencePriority.acquire(owner)
  try { NativeCollectorVisionEngine.prepare(context).use { engine ->
   for(id in listOf("727511d8-be1a-4c2e-92f6-5cc9d67a2b0c", "c9c6977d-0e14-47c1-89c0-be376f2675f3")) {
    val photo=BitmapFactory.decodeFile(File(context.noBackupFilesDir,"fra-replay/$id.jpg").path)
    assertNotNull(photo)
    val scene=Bitmap.createBitmap(800,1100,Bitmap.Config.ARGB_8888)
    try {
     scene.eraseColor(Color.rgb(110,110,110))
     Canvas(scene).drawBitmap(photo,null,Rect(100,130,700,970),Paint(Paint.FILTER_BITMAP_FLAG))
     val result=engine.scan(scene)
     android.util.Log.i("CollectorCoverageTest", "$id -> ${result.hits} present=${result.present}")
     assertTrue(result.present)
     assertTrue("Reference printing missing from candidates: $id", result.hits.any { it.cardId == id })
    } finally { photo.recycle();scene.recycle() }
   }
  } } finally { EdScanInferencePriority.release(owner) }
 }
}
