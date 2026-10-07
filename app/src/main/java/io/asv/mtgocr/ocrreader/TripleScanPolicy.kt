package io.asv.mtgocr.ocrreader

/** Pure arbitration: one camera, bounded fallback, no invented language or edition. */
object TripleScanPolicy {
    const val FRAME_INTERVAL_MS=250L
    const val AR_GRACE_MS=450L
    const val OCR_INTERVAL_MS=1200L
    fun useVisual(now:Long,arReadyAt:Long,lastTrackedAt:Long,tracked:Boolean):Boolean =
        !tracked && now-maxOf(arReadyAt,lastTrackedAt)>=AR_GRACE_MS
    fun visualReliable(score:Float,runner:Float?,readings:Int)=score.isFinite() && runner!=null && runner.isFinite() && score>=.70f && score-runner>=.08f && readings>=2
    // Preview only: the strict confirmation margin above stays unchanged.
    fun visualCandidate(score:Float,readings:Int)=score.isFinite() && ((score>=.85f && readings>=1) || (score>=.70f && readings>=2))
    fun auditDue(now:Long,lastAudit:Long)=now-lastAudit>=1500L
    fun language(code:String,confidence:Float):String?=code.takeIf { confidence>=.80f && it.isNotBlank() && it!="und" }
    fun agrees(expected:String,observed:String)=normalize(expected)==normalize(observed)
    private fun normalize(s:String)=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"),"").lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"),"")
}
