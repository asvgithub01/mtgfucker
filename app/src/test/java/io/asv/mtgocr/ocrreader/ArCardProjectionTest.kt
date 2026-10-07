package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class ArCardProjectionTest {
    private val identity=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)
    @Test fun projectsCenterAndFlipsScreenY() {
        assertArrayEquals(floatArrayOf(100f,200f),ArCardProjection.project(identity,floatArrayOf(0f,0f,0f),200,400),0.001f)
        assertArrayEquals(floatArrayOf(0f,0f),ArCardProjection.project(identity,floatArrayOf(-1f,1f,0f),200,400),0.001f)
    }
    @Test fun rejectsBehindCameraAndClippedDepth() {
        val behind=identity.copyOf().apply { this[15]=-1f }
        assertNull(ArCardProjection.project(behind,floatArrayOf(0f,0f,0f),200,400))
        assertNull(ArCardProjection.project(identity,floatArrayOf(0f,0f,2f),200,400))
    }
    @Test fun rejectsInvalidInputAndNonFiniteCoordinates() {
        assertNull(ArCardProjection.project(identity,floatArrayOf(Float.NaN,0f,0f),200,400))
        assertNull(ArCardProjection.project(FloatArray(0),floatArrayOf(0f,0f,0f),200,400))
        assertNull(ArCardProjection.project(identity,floatArrayOf(0f,0f,0f),0,400))
    }
    @Test fun perspectiveDivisionUsesColumnMajorTranslation() {
        val matrix=identity.copyOf().apply { this[12]=1f; this[15]=2f }
        assertArrayEquals(floatArrayOf(150f,200f),ArCardProjection.project(matrix,floatArrayOf(0f,0f,0f),200,400),0.001f)
    }
}
