package io.asv.mtgocr.ocrreader

/** Worker-owned admission. One slow lane per NEW capture, no queued images or ML overlap. */
internal class TripleFrameScheduler {
    enum class Lane { OCR, EDSCAN, IDLE }
    private var last=Lane.IDLE
    private var ocrAt:Long?=null
    private var followUpUntil=0L
    private var revalidationBorrowedOcr=false
    private var borrowedOcrTurn=false
    fun visualResult(reason:String, now:Long) {
        followUpUntil=if(reason=="readings") now+TripleScanPolicy.EVIDENCE_GAP_MS else 0L
    }
    fun hasFollowUp(now:Long)=followUpUntil>now && !borrowedOcrTurn
    fun reset() { revalidationBorrowedOcr=false;last=Lane.IDLE;ocrAt=null;followUpUntil=0L;borrowedOcrTurn=false }
    fun next(now:Long,visualDue:Boolean,ocrAvailable:Boolean,ocrAuditingSameIdentity:Boolean,revalidationDue:Boolean=false):Lane {
        val interval=if(ocrAuditingSameIdentity) 3000L else TripleScanPolicy.OCR_INTERVAL_MS
        val ocrDue=ocrAvailable && (ocrAt==null || now-requireNotNull(ocrAt)>=interval)
        val revalidate=revalidationDue && visualDue && (!revalidationBorrowedOcr || !ocrDue)
        val followUp=visualDue && hasFollowUp(now) && !(revalidationBorrowedOcr && ocrDue)
        val lane=when {
            revalidate -> Lane.EDSCAN
            followUp -> Lane.EDSCAN
            ocrDue && (!visualDue || last!=Lane.OCR) -> Lane.OCR
            visualDue -> Lane.EDSCAN
            else -> Lane.IDLE
        }
        if(revalidate) revalidationBorrowedOcr=ocrAvailable
        if(lane==Lane.OCR || !ocrAvailable) revalidationBorrowedOcr=false
        if(followUp) { followUpUntil=0L;borrowedOcrTurn=ocrAvailable }
        if(lane==Lane.OCR || !ocrAvailable) borrowedOcrTurn=false
        if(lane!=Lane.IDLE) last=lane
        if(lane==Lane.OCR) ocrAt=now
        return lane
    }
}
