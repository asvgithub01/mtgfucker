package io.asv.mtgocr.ocrreader

/** Slow inference is not a fresh observation until the current camera crop corroborates it. */
internal object TripleSlowFramePolicy {
    const val REVALIDATE_AFTER_MS=1800L
    const val MAX_PROCESSING_MS=15000L
    fun matches(before:FloatArray?,current:FloatArray?,started:Long,now:Long):Boolean =
        now>=started && now-started<=MAX_PROCESSING_MS && before!=null && current!=null &&
            before.isNotEmpty() && before.size==current.size && before.all { it.isFinite() } &&
            current.all { it.isFinite() } && TripleRecognitionArbiter.distance(before,current)<=.08f
}
