package io.asv.mtgocr.ocrreader

import android.graphics.PointF
import kotlin.math.hypot

data class AutoCaptureDecision(val progress: Float, val shouldCapture: Boolean)

/** Requires a coherent card outline for several frames before firing the shutter. */
class AutoCaptureStability(
    private val requiredFrames: Int = 6,
    private val requiredMillis: Long = 550L,
    private val maximumCornerMovement: Float = .022f
) {
    private var previous: Array<PointF>? = null
    private var stableFrames = 0
    private var stableSince = 0L

    fun observe(corners: Array<PointF>, confidence: Double, now: Long): AutoCaptureDecision {
        if (corners.size != 4 || confidence < MIN_CONFIDENCE) {
            reset()
            return AutoCaptureDecision(0f, false)
        }
        val last = previous
        val movement = if (last == null) Float.MAX_VALUE else corners.indices.maxOf { index ->
            hypot(corners[index].x - last[index].x, corners[index].y - last[index].y)
        }
        if (last == null || movement > maximumCornerMovement) {
            stableFrames = 1
            stableSince = now
        } else {
            stableFrames++
        }
        previous = corners.map { source ->
            PointF().apply {
                x = source.x
                y = source.y
            }
        }.toTypedArray()
        val frameProgress = if (requiredFrames <= 0) 1f else stableFrames / requiredFrames.toFloat()
        val timeProgress = if (requiredMillis <= 0L) 1f
            else (now - stableSince) / requiredMillis.toFloat()
        val progress = minOf(frameProgress, timeProgress).coerceIn(0f, 1f)
        return AutoCaptureDecision(
            progress,
            stableFrames >= requiredFrames && now - stableSince >= requiredMillis
        )
    }

    fun reset() {
        previous = null
        stableFrames = 0
        stableSince = 0L
    }

    private companion object {
        const val MIN_CONFIDENCE = .55
    }
}
