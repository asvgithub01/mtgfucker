package io.asv.mtgocr.ocrreader

/** Visual confidence only; final identity/freshness arbitration stays on main. No relaxed thresholds. */
internal class EdScanFastPolicy {
    data class Reading(val confirmed:Boolean,val reason:String,val latencyMs:Long)
    private val confirmation=TripleVisualConfirmation()
    private var id:String?=null
    private var since=0L
    private var previousAt=0L
    fun reset() { confirmation.reset();id=null;since=0;previousAt=0 }
    fun observe(present:Boolean,next:String?,score:Float?,runner:Float?,captureAt:Long,completedAt:Long):Reading {
        if(!present) { reset();return Reading(false,"no_detection",0) }
        if(next!=id || captureAt-previousAt>TripleScanPolicy.EVIDENCE_GAP_MS) { since=captureAt;id=next }
        previousAt=captureAt
        if(completedAt<captureAt || completedAt-captureAt>2500) {
            confirmation.reset();return Reading(false,"stale",completedAt-captureAt)
        }
        val r=confirmation.observe(next,score,runner,captureAt)
        return Reading(r.reliable,r.reason,(completedAt-since).coerceAtLeast(0))
    }
}
