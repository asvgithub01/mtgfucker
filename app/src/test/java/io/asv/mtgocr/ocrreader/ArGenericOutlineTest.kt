package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test

class ArGenericOutlineTest {
    @Test fun identityAndRotationProjection() {
        assertArrayEquals(floatArrayOf(25f,75f),ArGenericOutline.project(.25f,.75f,floatArrayOf(0f,0f,100f,0f,0f,100f)),.001f)
        assertArrayEquals(floatArrayOf(25f,25f),ArGenericOutline.project(.25f,.75f,floatArrayOf(100f,0f,100f,100f,0f,0f)),.001f)
    }
    @Test fun cropOffsetAndScaleProjection() {
        assertArrayEquals(floatArrayOf(40f,80f),ArGenericOutline.project(.5f,.5f,floatArrayOf(-10f,-20f,90f,-20f,-10f,180f)),.001f)
    }
    @Test fun discardsExpiredFutureResizedAndRotatedFrames() {
        val q=ArGenericOutline(FloatArray(8),1000,100,200,0)
        assertTrue(q.valid(1250,100,200,0))
        assertFalse(q.valid(1251,100,200,0))
        assertFalse(q.valid(999,100,200,0))
        assertFalse(q.valid(1100,200,100,0))
        assertFalse(q.valid(1100,100,200,1))
    }
}
