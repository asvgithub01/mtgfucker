package io.asv.mtgocr.ocrreader

object TripleOcrPrintingPolicy {
    const val MAX_AR_REFERENCES=1000
    fun singleSet(sets:List<String>,complete:Boolean)=complete && sets.isNotEmpty() && sets.none { it.isBlank() } && sets.map { it.lowercase(java.util.Locale.ROOT) }.distinct().size==1
    fun hasCapacity(base:Int,added:Int,pending:Int)=base+added+pending<MAX_AR_REFERENCES
}
