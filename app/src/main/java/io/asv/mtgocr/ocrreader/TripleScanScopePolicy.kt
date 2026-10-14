package io.asv.mtgocr.ocrreader

/** Empty printing metadata stays unresolved; a known printing never escapes the user lock. */
internal object TripleScanScopePolicy {
    fun allows(sets:Set<String>,printingSet:String)=sets.isEmpty() || printingSet.isBlank() ||
        printingSet.uppercase(java.util.Locale.ROOT) in sets
}
