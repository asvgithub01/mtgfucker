package io.asv.mtgocr.ocrreader

import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.BitmapFactory
import io.asv.collectorvision.EdScanJobs
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OcrFramePhotoDeviceTest {
    @Test fun flagOffWritesNothingAndEnabledPhotoKeepsAcceptedFrameRotation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=ScannerSettings.saveOcrPhotos(context)
        var key: String?=null
        try {
            val width=40; val height=60
            val nv21=ByteArray(width*height*3/2) { if(it<width*height) 60 else 128.toByte() }
            val snapshot=OcrFramePhoto.snapshot(654,nv21,width,height,1)
            nv21.fill(220.toByte()) // Simulates enhancer/camera buffer reuse after the OCR snapshot.
            ScannerSettings.setSaveOcrPhotos(context,false)
            assertNull(OcrFramePhoto.save(context,snapshot,"Synthetic card"))
            ScannerSettings.setSaveOcrPhotos(context,true)
            key=OcrFramePhoto.save(context,snapshot,"Synthetic card")
            assertNotNull(key)
            val dir=EdScanJobs.directory(context); val meta=File(dir,"$key.json")
            val until=android.os.SystemClock.elapsedRealtime()+10000
            while(!meta.exists() && android.os.SystemClock.elapsedRealtime()<until) Thread.sleep(100)
            assertTrue(meta.exists())
            assertEquals(654,JSONObject(meta.readText()).getInt("frameId"))
            val bitmap=BitmapFactory.decodeFile(File(dir,"$key.jpg").path)
            assertEquals(60,bitmap.width); assertEquals(40,bitmap.height)
            assertTrue(android.graphics.Color.red(bitmap.getPixel(20,20))<100)
            bitmap.recycle()
            assertFalse(File(dir,"$key.link.json").exists()) // Never modifies or associates user inventory.
        } finally {
            ScannerSettings.setSaveOcrPhotos(context,original)
            key?.let { id -> listOf(".jpg",".json").forEach { File(EdScanJobs.directory(context),id+it).delete() } }
        }
    }
}
