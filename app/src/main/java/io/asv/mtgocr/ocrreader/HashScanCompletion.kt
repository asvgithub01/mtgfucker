package io.asv.mtgocr.ocrreader

/** Exactly-once barrier; disabled or duplicate callbacks cannot publish a partial result. */
internal class HashScanCompletion(
    ocr: Boolean,
    language: Boolean = false,
    private val onComplete: () -> Unit
) {
    private val pending = buildSet {
        add("visual")
        if (ocr) {
            add("title")
            add("printing")
        }
        if (language) add("language")
    }.toMutableSet()
    fun finish(stage: String) {
        val complete = synchronized(pending) { pending.remove(stage) && pending.isEmpty() }
        if (complete) onComplete()
    }
}
