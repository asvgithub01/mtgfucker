package io.asv.mtgocr.ocrreader

import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class HashGuideTapDeviceTest {
    @Test fun onlyTapInsideGuideCaptures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = ExperimentalCardGuideView(instrumentation.targetContext)
            view.layout(0, 0, 630, 1000)
            var clicks = 0
            view.onCardTap = { clicks++ }
            fun touch(action: Int, x: Float, y: Float) {
                MotionEvent.obtain(0, 0, action, x, y, 0).let { view.onTouchEvent(it); it.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, 315f, 500f)
            touch(MotionEvent.ACTION_UP, 315f, 500f)
            assertEquals(1, clicks)
            touch(MotionEvent.ACTION_DOWN, 1f, 1f)
            touch(MotionEvent.ACTION_UP, 1f, 1f)
            assertEquals(1, clicks)
            touch(MotionEvent.ACTION_DOWN, 315f, 500f)
            touch(MotionEvent.ACTION_MOVE, 500f, 600f)
            touch(MotionEvent.ACTION_UP, 500f, 600f)
            assertEquals(1, clicks)
            touch(MotionEvent.ACTION_DOWN, 315f, 500f)
            touch(MotionEvent.ACTION_CANCEL, 315f, 500f)
            touch(MotionEvent.ACTION_UP, 315f, 500f)
            assertEquals(1, clicks)
        }
    }
}
