package io.asv.mtgocr.ocrreader

/** One compiled database may cover several selected sets; never infer coverage from its ID. */
internal object TripleArPackagePolicy {
    fun covers(requested:Set<String>,labels:List<String>):Boolean {
        val coverage=labels.map { it.substringAfterLast(" · ","").substringBefore(" #").uppercase(java.util.Locale.ROOT) }.toSet()
        return requested.isNotEmpty() && requested.all { it.uppercase(java.util.Locale.ROOT) in coverage }
    }
}
