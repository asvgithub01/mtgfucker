package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** No camera or collection writes: the retained full frame can recover an empty wrong crop. */
class HashManualCropDeviceTest {
    @Test fun fullFrameAllowsRecoveringCardOutsideOriginalCornersAndReleasesPhoto() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = CardCropAdjustView(instrumentation.targetContext)
            val full = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
                Canvas(this).drawRect(430f, 110f, 790f, 630f, Paint().apply { color = Color.RED })
            }
            val replacement = full.copy(Bitmap.Config.ARGB_8888, true)
            view.setPhoto(full, Rect(0, 100, 380, 640))
            val wrong = checkNotNull(view.extractCardBitmap())
            assertEquals(Color.WHITE, wrong.getPixel(wrong.width / 2, wrong.height / 2))
            wrong.recycle()
            view.setPhoto(replacement, Rect(420, 100, 800, 640))
            assertTrue(full.isRecycled)
            val corrected = checkNotNull(view.extractCardBitmap())
            assertEquals(Color.RED, corrected.getPixel(corrected.width / 2, corrected.height / 2))
            corrected.recycle()
            view.clearPhoto()
            assertTrue(replacement.isRecycled)
            assertNull(view.extractCardBitmap())
        }
    }
}
