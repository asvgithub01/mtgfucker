package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test

class CollectorVisionMathTest {
    @Test fun halfValuesIncludeSubnormalAndInfinity() {
        assertEquals(1f,CollectorVisionMath.half(0x3c00),0f)
        assertEquals(-2f,CollectorVisionMath.half(0xc000),0f)
        assertEquals(0f,CollectorVisionMath.half(0),0f)
        assertEquals(Math.scalb(1f,-24),CollectorVisionMath.half(1),0f)
        assertTrue(CollectorVisionMath.half(0x7c00).isInfinite())
        assertTrue(CollectorVisionMath.half(0x7e00).isNaN())
    }
    @Test fun embeddingIsUnitLength() {
        val normalized=CollectorVisionMath.normalize(floatArrayOf(3f,4f))
        assertEquals(.6f,normalized[0],.00001f)
        assertEquals(.8f,normalized[1],.00001f)
    }
    @Test(expected=IllegalArgumentException::class) fun zeroEmbeddingRejected() {
        CollectorVisionMath.normalize(floatArrayOf(0f,0f))
    }
    @Test fun cornerOrderingMaintainsConvexCardAndShortestTop() {
        val points=CollectorVisionMath.ordered(floatArrayOf(.8f,.9f,.2f,.1f,.2f,.9f,.8f,.1f),800,1200)
        assertTrue(CollectorVisionMath.usable(points))
        assertEquals(points[0].y,points[1].y,0f)
    }
    @Test fun partialDegenerateAndNonFiniteQuadsRejected() {
        assertFalse(CollectorVisionMath.usable(List(4) { Corner(.5f,.5f) }))
        assertFalse(CollectorVisionMath.usable(listOf(Corner(.1f,.1f),Corner(.9f,.1f),Corner(.2f,.2f),Corner(.1f,.9f))))
        assertEquals(emptyList<Corner>(),CollectorVisionMath.ordered(FloatArray(8) { Float.NaN },800,1200))
    }
    @Test fun pinsHaveExpectedSizesAndIndependentCatalogHash() {
        assertEquals(42092791L,CollectorVisionAssets.assets.sumOf { it.bytes })
        assertEquals(4,CollectorVisionAssets.assets.size)
        assertTrue(CollectorVisionAssets.assets.all { it.sha256.matches(Regex("[0-9a-f]{64}")) })
    }
}
