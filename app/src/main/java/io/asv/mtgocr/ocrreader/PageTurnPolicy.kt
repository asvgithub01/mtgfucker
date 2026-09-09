package io.asv.mtgocr.ocrreader

import kotlin.math.abs

/** Shared paging thresholds so the gesture and its finishing animation stay in sync. */
internal object PageTurnPolicy {
    // About 15% of the visible width is enough: deliberate, but comfortable one-handed.
    const val DRAG_DISTANCE_FRACTION = .60f
    const val COMMIT_FRACTION = .25f
    const val FLING_VELOCITY_PX_PER_MS = .45f

    fun dragFraction(distance: Float, pageWidth: Int): Float =
        (distance / (pageWidth.coerceAtLeast(1) * DRAG_DISTANCE_FRACTION)).coerceIn(-1f, 1f)

    fun shouldCommit(fraction: Float, velocityX: Float = 0f, targetReady: Boolean): Boolean =
        targetReady && (
            abs(fraction) >= COMMIT_FRACTION ||
                abs(velocityX) >= FLING_VELOCITY_PX_PER_MS && fraction * velocityX > 0f
            )

    fun shouldCancelForReversal(
        initialDirection: Int,
        furthestDistance: Float,
        currentDistance: Float,
        tolerance: Float
    ): Boolean {
        if (initialDirection == 0) return false
        val distanceInInitialDirection = currentDistance * initialDirection
        return distanceInInitialDirection <= 0f ||
            furthestDistance - distanceInInitialDirection >= tolerance
    }
}
