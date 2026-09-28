package io.asv.mtgocr.ocrreader
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.*
import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
class EdScanCatalogTimingDeviceTest {
    @Test fun comparesBaseAndFraOnSameFramesWithoutBackgroundInference() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val owner=Any(); EdScanInferencePriority.acquire(owner)
        val photo=BitmapFactory.decodeFile(File(context.noBackupFilesDir,"fra-replay/730d8c28-1e58-4b8e-89e9-445d154d2e83.jpg").path)
        assertNotNull(photo)
        val scene=Bitmap.createBitmap(800,1100,Bitmap.Config.ARGB_8888)
        scene.eraseColor(Color.GRAY);Canvas(scene).drawBitmap(photo,null,Rect(100,130,700,970),Paint(Paint.FILTER_BITMAP_FLAG))
        val rows=JSONArray()
        try {
            // ABBA order reduces bias from warm-up and thermal drift.
            for(full in listOf(false,true,true,false)) {
                NativeCollectorVisionEngine.prepareCatalogComparison(context,full).use { engine ->
                    repeat(2) { engine.scan(scene) }
                    repeat(6) {
                        val r=engine.scan(scene); assertTrue(r.present)
                        rows.put(JSONObject().put("fra",full).put("detectionMs",r.detectionMs).put("recognitionMs",r.recognitionMs)
                            .put("candidate",r.hits.firstOrNull()?.cardId))
                    }
                }
            }
            File(context.noBackupFilesDir,"fra-replay/timing-abba.json").writeText(rows.toString())
            android.util.Log.i("EdScanTimingTest",rows.toString())
        } finally { photo.recycle();scene.recycle();EdScanInferencePriority.release(owner) }
    }
}
