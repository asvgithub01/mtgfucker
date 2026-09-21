package io.asv.mtgocr.ocrreader

/** Starts the cheap hash from a stable preview frame, before CameraX takes a JPEG. */
internal object HashLiveScanPolicy {
    const val MIN_PROGRESS = .34f
    const val MIN_FRAMES = 3

    fun shouldAnalyze(progress: Float, frames: Int): Boolean =
        progress >= MIN_PROGRESS && frames >= MIN_FRAMES
}
