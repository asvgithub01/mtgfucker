package io.asv.mtgocr.ocrreader

/** Only consecutive, individually strong independent frames count towards confirmation. */
internal class TripleVisualConfirmation {
    data class Reading(val reliable: Boolean,val count: Int,val reason: String,val elapsedMs: Long)
    private var id=""
    private var at=-1L
    private var started=0L
    private var count=0
    fun reset() { id="";at=-1;count=0 }
    fun observe(next: String?,score: Float?,runner: Float?,now: Long,processingStartedAt:Long=now): Reading {
        val reason=when {
            next==null || score==null || !score.isFinite() -> "no_candidate"
            score<TripleScanPolicy.VISUAL_MIN_SCORE -> "score"
            runner==null || !runner.isFinite() -> "no_runner"
            score-runner<TripleScanPolicy.VISUAL_MIN_MARGIN -> "margin"
            else -> "strong"
        }
        // A slow result may exclude processing time ONLY after validation against a fresh frame.
        // Actual inactivity still expires the streak; repeated timestamps still do not vote.
        if(at>=0 && now<=at) return Reading(false,count,"out_of_order",(now-started).coerceAtLeast(0))
        if(next!=id || at<0 || processingStartedAt-at>TripleScanPolicy.EVIDENCE_GAP_MS) { id=next.orEmpty();count=0;started=now }
        if(reason!="strong") {
            // A dip after an already confirmed card starts a new retry, not a minutes-long scan.
            if(count>=TripleScanPolicy.VISUAL_STRONG_READINGS) started=now
            count=0
        } else if(now>at) count++
        at=now
        val reliable=count>=TripleScanPolicy.VISUAL_STRONG_READINGS
        return Reading(reliable,count,if(reason=="strong") if(reliable) "confirmed" else "readings" else reason,now-started)
    }
}
