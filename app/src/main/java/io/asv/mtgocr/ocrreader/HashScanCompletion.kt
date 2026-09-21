package io.asv.mtgocr.ocrreader

/** Exactly-once barrier; disabled or duplicate callbacks cannot publish a partial result. */
internal class HashScanCompletion(ocr: Boolean, private val onComplete: () -> Unit) {
    private val pending = (if (ocr) setOf("visual", "title", "printing") else setOf("visual")).toMutableSet()
    fun finish(stage: String) {
        val complete = synchronized(pending) { pending.remove(stage) && pending.isEmpty() }
        if (complete) onComplete()
    }
}
