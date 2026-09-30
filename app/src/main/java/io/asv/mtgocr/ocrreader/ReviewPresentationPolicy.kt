package io.asv.mtgocr.ocrreader

/** One visible item per captured photo, keeping its original evidence key for safe apply checks. */
object ReviewPresentationPolicy {
    fun reviewed(evidence: List<ScanReviewStore.Entry>): Boolean = evidence.isNotEmpty() && evidence.all { it.state=="reviewed" }
    fun candidateState(verified: Boolean, samePrinting: Boolean): String =
        if(!verified) "inconclusive" else if(samePrinting) "consistent" else "conflict"

    fun primary(entries: List<ScanReviewStore.Entry>): List<ScanReviewStore.Entry> =
        entries.groupBy { it.photoKey }.values.map { evidence ->
            evidence.minWith(compareBy<ScanReviewStore.Entry> {
                when(it.state) { "conflict"->0; "suggestion"->1; "pending"->2; "inconclusive"->3; "consistent"->4; else->5 }
            }.thenBy { if(it.visual) 0 else 1 })
        }
}
