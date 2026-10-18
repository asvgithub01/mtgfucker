package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdScanContourViewTest {
    @Test fun drawsStatusWithoutDetectingAndClearsStaleOrResizedGeometry() {
        val test=InstrumentationRegistry.getInstrumentation()
        test.runOnMainSync {
            val view=EdScanContourView(test.targetContext)
            view.layout(0,0,200,300)
            val p=floatArrayOf(20f,30f,180f,30f,180f,270f,20f,270f)
            val bitmap=Bitmap.createBitmap(200,300,Bitmap.Config.ARGB_8888)
            fun pixel():Int { bitmap.eraseColor(Color.TRANSPARENT);view.draw(Canvas(bitmap));return bitmap.getPixel(100,30) }
            view.showContour(p,false,SystemClock.elapsedRealtime());assertEquals(Color.YELLOW,pixel())
            view.showContour(p,true,SystemClock.elapsedRealtime());assertEquals(Color.GREEN,pixel())
            view.showContour(p,true,SystemClock.elapsedRealtime()-2600);assertEquals(Color.TRANSPARENT,pixel())
            view.showContour(p,true,SystemClock.elapsedRealtime());view.layout(0,0,180,300);assertEquals(Color.TRANSPARENT,pixel())
            view.clearContour();assertEquals(Color.TRANSPARENT,pixel());bitmap.recycle()
        }
    }
}
