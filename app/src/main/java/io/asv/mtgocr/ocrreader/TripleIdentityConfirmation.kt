package io.asv.mtgocr.ocrreader

/** Identity authorizes a card; only independent whole-card printing votes certify its edition. */
internal class TripleIdentityConfirmation(singleReading:Boolean=false) {
    data class Result(val evidence:EdScanFastIdentity.Evidence,
                      val confirmation:TripleVisualConfirmation.Reading,
                      val printing:String?,val printingConfirmed:Boolean)
    private val identity=TripleVisualConfirmation(if(singleReading) 1 else 2)
    private val printing=TripleVisualConfirmation()
    fun reset() { identity.reset();printing.reset() }
    fun observe(candidates:List<EdScanFastIdentity.Candidate>,wholeCard:Boolean,at:Long,processingAt:Long=at):Result {
        val evidence=EdScanFastIdentity.resolve(candidates)
        val confirmed=identity.observe(evidence.identity,evidence.score,evidence.runner,at,processingAt)
        val ordered=candidates.sortedByDescending { it.score }
        val top=ordered.firstOrNull()
        val edition=printing.observe(if(wholeCard) top?.id else null,
            if(wholeCard) top?.score else null,if(wholeCard) ordered.getOrNull(1)?.score else null,at,processingAt)
        val exact=confirmed.reliable && edition.reliable
        return Result(evidence,confirmed,if(exact) top?.id else evidence.printing,exact)
    }
}
