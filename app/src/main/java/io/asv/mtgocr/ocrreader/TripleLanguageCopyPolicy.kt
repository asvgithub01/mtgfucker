package io.asv.mtgocr.ocrreader

/** Per-capture physical evidence. Never interpret a different known language as an edit. */
internal class TripleLanguageCopyPolicy {
    enum class Action { KEEP, ASSIGN, WAIT, CONFLICT, SPLIT }
    private var key=""
    private var pending=""
    private var first=0L
    private var last=-1L
    private var samples=0
    fun reset() { key="";pending="";last=-1;samples=0 }

    fun observe(event:Long, stored:String?, storedOrigin:String, manual:Boolean, code:String?,
                exactTitle:Boolean, identityMatched:Boolean, hasPhoto:Boolean,
                sampleAt:Long, now:Long):Action {
        if(code.isNullOrBlank() || code=="und" || !identityMatched || sampleAt>now || now-sampleAt>2500)
            return Action.KEEP
        val nextKey="$event:${stored.orEmpty()}"
        if(key!=nextKey) { reset();key=nextKey }
        val physicalUpgrade=code==stored && storedOrigin=="printing" && !manual
        if(code==stored && !physicalUpgrade) { pending="";samples=0;last=sampleAt;return Action.KEEP }
        if(manual || (!stored.isNullOrBlank() && storedOrigin=="printing" && !physicalUpgrade)) return Action.CONFLICT
        if(sampleAt<=last) return Action.WAIT // title + rules of the same frame are not two votes
        if(code!=pending || last<0 || sampleAt-last>TripleScanPolicy.EVIDENCE_GAP_MS) {
            pending=code;first=sampleAt;samples=0
        }
        last=sampleAt;samples++
        val unknown=stored.isNullOrBlank() || stored=="und" || physicalUpgrade
        val needed=if(unknown) if(exactTitle) 1 else 2 else if(exactTitle) 2 else 3
        if(samples<needed || (needed>1 && sampleAt-first<600)) return Action.WAIT
        if(unknown) return Action.ASSIGN
        // The old photo belongs to the old copy; never manufacture a new row with it.
        return if(hasPhoto) Action.SPLIT else Action.CONFLICT
    }
}
