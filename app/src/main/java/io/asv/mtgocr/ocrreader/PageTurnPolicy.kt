package io.asv.mtgocr.ocrreader

import kotlin.math.abs

/** Shared paging thresholds so the gesture and its finishing animation stay in sync. */
internal object PageTurnPolicy {
    // A completed turn now needs roughly one quarter of the visible width instead of half.
    const val DRAG_DISTANCE_FRACTION = .72f
    const val COMMIT_FRACTION = .34f

    fun dragFraction(distance: Float, pageWidth: Int): Float =
        (distance / (pageWidth.coerceAtLeast(1) * DRAG_DISTANCE_FRACTION)).coerceIn(-1f, 1f)

    fun shouldCommit(fraction: Float, targetReady: Boolean): Boolean =
        targetReady && abs(fraction) >= COMMIT_FRACTION

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
