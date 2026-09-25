package io.asv.collectorvision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in: may download pinned assets. Run with -e collectorvisionAssets true. */
class NativeCollectorVisionEngineDeviceTest {
    @Test fun nativeCpuPipelineLoadsPinnedAssetsAndEvaluatesBitmap() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("Explicit asset-download opt-in required",args.getString("collectorvisionAssets")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        NativeCollectorVisionEngine.prepare(context).use { engine ->
            val bitmap=Bitmap.createBitmap(640,880,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.GRAY)
            try {
                val result=engine.scan(bitmap)
                assertTrue(result.sharpness.isFinite())
                assertTrue(result.detectionMs>=0)
                assertFalse("Uniform frame must not identify a card",result.present)
                assertTrue(result.hits.isEmpty())
            } finally { bitmap.recycle() }
            val path=args.getString("collectorvisionImage")
            if (!path.isNullOrBlank()) {
                val image=requireNotNull(BitmapFactory.decodeFile(path))
                try {
                    val result=engine.scan(image)
                    android.util.Log.i("CollectorVisionNativeTest",result.toString())
                    assertTrue("Fixture must contain card",result.present)
                    assertEquals(3,result.hits.size)
                    assertTrue(result.hits.all { it.score.isFinite() })
                    val expected=args.getString("collectorvisionExpectedId")
                    if (!expected.isNullOrBlank()) assertEquals(expected,result.hits.first().cardId)
                } finally { image.recycle() }
            }
        }
    }
}
