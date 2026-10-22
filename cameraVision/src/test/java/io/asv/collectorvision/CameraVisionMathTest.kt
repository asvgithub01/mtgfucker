package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test

class CameraVisionMathTest {
    @Test fun photoUsesWinningRotationWithoutMirroring() {
        val corners = listOf(Corner(0f,0f),Corner(1f,0f),Corner(1f,1f),Corner(0f,1f))
        assertEquals(corners, CameraVisionMath.photoCorners(corners, false))
        assertEquals(listOf(corners[2],corners[3],corners[0],corners[1]), CameraVisionMath.photoCorners(corners,true))
        assertEquals(corners, CameraVisionMath.photoCorners(CameraVisionMath.photoCorners(corners,true),true))
        assertTrue(CameraVisionMath.prefersRotated(listOf(Hit("a",.4f)), listOf(Hit("b",.8f))))
        assertFalse(CameraVisionMath.prefersRotated(listOf(Hit("a",.8f)), listOf(Hit("b",.8f))))
    }

    @Test fun uprightModeUsesUpperShortEdgeWithoutMirroring() {
        val expected=listOf(Corner(.1f,.1f),Corner(.9f,.15f),Corner(.8f,.9f),Corner(.2f,.85f))
        assertEquals(expected,CameraVisionMath.uprightCorners(expected))
        assertEquals(expected,CameraVisionMath.uprightCorners(expected.drop(2)+expected.take(2)))
        val detected=CameraVisionMath.ordered(expected.flatMap { listOf(it.x,it.y) }.toFloatArray(),800,1200)
        assertEquals(expected,CameraVisionMath.uprightCorners(detected))
        assertEquals(emptyList<Corner>(),CameraVisionMath.uprightCorners(emptyList()))
    }
    @Test fun uprightModeDoesNotMutateOriginalGeometry() {
        val input=listOf(Corner(.8f,.9f),Corner(.2f,.9f),Corner(.1f,.1f),Corner(.9f,.1f))
        val saved=input.toList()
        assertEquals(listOf(input[2],input[3],input[0],input[1]),CameraVisionMath.uprightCorners(input))
        assertEquals(saved,input)
    }
    @Test fun halfValuesIncludeSubnormalAndInfinity() {
        assertEquals(1f,CameraVisionMath.half(0x3c00),0f)
        assertEquals(-2f,CameraVisionMath.half(0xc000),0f)
        assertEquals(0f,CameraVisionMath.half(0),0f)
        assertEquals(Math.scalb(1f,-24),CameraVisionMath.half(1),0f)
        assertTrue(CameraVisionMath.half(0x7c00).isInfinite())
        assertTrue(CameraVisionMath.half(0x7e00).isNaN())
    }
    @Test fun embeddingIsUnitLength() {
        val normalized=CameraVisionMath.normalize(floatArrayOf(3f,4f))
        assertEquals(.6f,normalized[0],.00001f)
        assertEquals(.8f,normalized[1],.00001f)
    }
    @Test(expected=IllegalArgumentException::class) fun zeroEmbeddingRejected() {
        CameraVisionMath.normalize(floatArrayOf(0f,0f))
    }
    @Test fun cornerOrderingMaintainsConvexCardAndShortestTop() {
        val points=CameraVisionMath.ordered(floatArrayOf(.8f,.9f,.2f,.1f,.2f,.9f,.8f,.1f),800,1200)
        assertTrue(CameraVisionMath.usable(points))
        assertEquals(points[0].y,points[1].y,0f)
    }
    @Test fun partialDegenerateAndNonFiniteQuadsRejected() {
        assertFalse(CameraVisionMath.usable(List(4) { Corner(.5f,.5f) }))
        assertFalse(CameraVisionMath.usable(listOf(Corner(.1f,.1f),Corner(.9f,.1f),Corner(.2f,.2f),Corner(.1f,.9f))))
        assertEquals(emptyList<Corner>(),CameraVisionMath.ordered(FloatArray(8) { Float.NaN },800,1200))
    }
    @Test fun pinsHaveExpectedSizesAndIndependentCatalogHash() {
        assertEquals(42092791L,CameraVisionAssets.assets.sumOf { it.bytes })
        assertEquals(4,CameraVisionAssets.assets.size)
        assertTrue(CameraVisionAssets.assets.all { it.sha256.matches(Regex("[0-9a-f]{64}")) })
    }
}
