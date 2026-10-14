package io.asv.mtgocr.ocrreader

/** Pure arbitration: one camera, bounded fallback, no invented language or edition. */
object TripleScanPolicy {
    const val FRAME_INTERVAL_MS=250L
    const val AR_GRACE_MS=450L
    const val OCR_INTERVAL_MS=1200L
    // Continuity between independent captures, not the 2500 ms freshness of each result.
    const val EVIDENCE_GAP_MS=6000L
    const val VISUAL_MIN_SCORE=.70f
    const val VISUAL_MIN_MARGIN=.08f
    const val VISUAL_STRONG_READINGS=2
    fun useVisual(now:Long,arReadyAt:Long,lastTrackedAt:Long,tracked:Boolean):Boolean =
        !tracked && now-maxOf(arReadyAt,lastTrackedAt)>=AR_GRACE_MS
    fun visualReliable(score:Float,runner:Float?,readings:Int)=score.isFinite() && runner!=null && runner.isFinite() && score>=VISUAL_MIN_SCORE && score-runner>=VISUAL_MIN_MARGIN && readings>=VISUAL_STRONG_READINGS
    // Preview only: the strict confirmation margin above stays unchanged.
    fun visualCandidate(score:Float,readings:Int)=score.isFinite() && ((score>=.85f && readings>=1) || (score>=.70f && readings>=2))
    // Bound sustained CPU duty without slowing the fast path or breaking the 2.5 s evidence gap.
    fun frameRestMs(workMs:Long)=(workMs/2).coerceIn(150L,650L)
    fun confirmationNeeded(pending:Boolean,needs:Boolean,reason:String)=
        pending || (needs && reason!="stale" && reason!="out_of_order")
    fun auditDue(now:Long,lastAudit:Long)=now-lastAudit>=1500L
    fun language(code:String,confidence:Float):String?=code.takeIf { confidence>=.80f && it.isNotBlank() && it!="und" }
    fun agrees(expected:String,observed:String)=normalize(expected)==normalize(observed)
    private fun normalize(s:String)=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"),"").lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"),"")
}
