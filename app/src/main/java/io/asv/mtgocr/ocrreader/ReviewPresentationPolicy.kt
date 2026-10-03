package io.asv.mtgocr.ocrreader

/** One visible item per captured photo, keeping its original evidence key for safe apply checks. */
object ReviewPresentationPolicy {
    /** Status belongs to the whole photo, not just its representative evidence row. */
    fun forTab(entries: List<ScanReviewStore.Entry>, resolved: Boolean, source: String,
               reopened: Set<String> = emptySet()): List<ScanReviewStore.Entry> {
        val evidence=entries.groupBy { it.photoKey }
        return primary(entries).filter { entry ->
            val done=entry.photoKey !in reopened && reviewed(evidence.getValue(entry.photoKey))
            done==resolved && (source=="all" || entry.source==source)
        }
    }
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
