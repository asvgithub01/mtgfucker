package io.asv.mtgocr.ocrreader

/** Worker-owned admission. One slow lane per NEW capture, no queued images or ML overlap. */
internal class TripleFrameScheduler {
    enum class Lane { OCR, EDSCAN, IDLE }
    private var last=Lane.IDLE
    private var ocrAt:Long?=null
    fun reset() { last=Lane.IDLE;ocrAt=null }
    fun next(now:Long,visualDue:Boolean,ocrAvailable:Boolean,ocrAuditingSameIdentity:Boolean):Lane {
        val interval=if(ocrAuditingSameIdentity) 3000L else TripleScanPolicy.OCR_INTERVAL_MS
        val ocrDue=ocrAvailable && (ocrAt==null || now-requireNotNull(ocrAt)>=interval)
        val lane=when {
            ocrDue && (!visualDue || last!=Lane.OCR) -> Lane.OCR
            visualDue -> Lane.EDSCAN
            else -> Lane.IDLE
        }
        if(lane!=Lane.IDLE) last=lane
        if(lane==Lane.OCR) ocrAt=now
        return lane
    }
}
