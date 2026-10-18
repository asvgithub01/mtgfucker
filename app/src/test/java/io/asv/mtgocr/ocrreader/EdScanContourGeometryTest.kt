package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class EdScanContourGeometryTest {
    private val unit=floatArrayOf(0f,0f,1f,0f,1f,1f,0f,1f)
    @Test fun projectsOffsetsAndPreviewCropWithoutClamping() {
        val view=floatArrayOf(-20f,10f,180f,10f,180f,310f,-20f,310f)
        assertArrayEquals(view,EdScanContourGeometry.project(unit,0,view),.0001f)
    }
    @Test fun inverseRotationUsesPhysicalCornersNotRecognitionOrientation() {
        val p=floatArrayOf(.2f,.3f,.7f,.3f,.7f,.8f,.2f,.8f)
        for(r in listOf(0,90,180,270)) {
            val rotated=FloatArray(8)
            for(i in 0..3) {
                val x=p[i*2];val y=p[i*2+1]
                val (a,b)=when(r) {90 -> 1-y to x;180 -> 1-x to 1-y;270 -> y to 1-x;else -> x to y}
                rotated[i*2]=a;rotated[i*2+1]=b
            }
            assertArrayEquals(p,EdScanContourGeometry.project(rotated,r,unit),.0001f)
        }
    }
    @Test fun rejectsInvalidGeometry() {
        assertNull(EdScanContourGeometry.project(FloatArray(4),0,unit))
        assertNull(EdScanContourGeometry.project(unit,45,unit))
        assertNull(EdScanContourGeometry.project(unit,0,unit.copyOf().also { it[0]=Float.NaN }))
    }
    @Test fun expiresFromCaptureNotFromLateUiUpdate() {
        assertTrue(EdScanContourGeometry.fresh(1000,3499))
        assertFalse(EdScanContourGeometry.fresh(1000,3500))
        assertFalse(EdScanContourGeometry.fresh(1000,999))
    }
}
