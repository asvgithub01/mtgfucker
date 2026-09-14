package io.asv.mtgocr.ocrreader

import android.graphics.PointF
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureStabilityTest {
    @Test
    fun capturesOnlyAfterEnoughStableFramesAndTime() {
        val stability = AutoCaptureStability(requiredFrames = 3, requiredMillis = 200)
        val corners = quad()

        assertFalse(stability.observe(corners, .9, 0).shouldCapture)
        assertFalse(stability.observe(corners, .9, 100).shouldCapture)
        assertTrue(stability.observe(corners, .9, 220).shouldCapture)
    }

    @Test
    fun movementRestartsTheStableWindow() {
        val stability = AutoCaptureStability(requiredFrames = 2, requiredMillis = 100)
        assertFalse(stability.observe(quad(), .9, 0).shouldCapture)
        assertFalse(stability.observe(quad(offset = .08f), .9, 150).shouldCapture)
        assertTrue(stability.observe(quad(offset = .08f), .9, 260).shouldCapture)
    }

    @Test
    fun lowConfidenceNeverCaptures() {
        val stability = AutoCaptureStability(requiredFrames = 1, requiredMillis = 0)
        assertFalse(stability.observe(quad(), .3, 1_000).shouldCapture)
    }

    private fun quad(offset: Float = 0f) = arrayOf(
        point(.2f + offset, .1f),
        point(.8f + offset, .1f),
        point(.8f + offset, .9f),
        point(.2f + offset, .9f)
    )

    // android.jar constructors are no-ops in local JVM tests; write the public fields directly.
    private fun point(xValue: Float, yValue: Float) = PointF().apply {
        x = xValue
        y = yValue
    }
}
