package io.asv.mtgocr.ocrreader
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.collectorvision.*
import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class FraRecognitionDeviceTest {
    @Test fun nativePipelineReportsReferenceScenesWithoutCollectionWrites() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        NativeCameraVisionEngine.prepare(context).use { engine ->
            for(id in listOf("c3ff8dd3-88a8-49dc-a59b-e2748680623c","730d8c28-1e58-4b8e-89e9-445d154d2e83","5e77fbf0-9d1f-4e7d-a02e-43065d11b0d9")) {
                val photo=BitmapFactory.decodeFile(File(context.noBackupFilesDir,"fra-replay/$id.jpg").path)
                assertNotNull("Missing private reference fixture",photo)
                val scene=Bitmap.createBitmap(800,1100,Bitmap.Config.ARGB_8888)
                try {
                    scene.eraseColor(Color.rgb(110,110,110))
                    Canvas(scene).drawBitmap(photo,null,Rect(100,130,700,970),Paint(Paint.FILTER_BITMAP_FLAG))
                    val result=engine.scan(scene)
                    android.util.Log.i("FraRecognitionTest","$id -> ${result.hits} present=${result.present}")
                    assertTrue(result.present)
                    // This is an index/inference smoke test, not a guarantee of printing-level ranking.
                    // Preserve exact ranking in the report, including mismatches; never apply to inventory.
                    assertTrue(result.hits.isNotEmpty())
                } finally { photo.recycle();scene.recycle() }
            }
        }
    }
}
