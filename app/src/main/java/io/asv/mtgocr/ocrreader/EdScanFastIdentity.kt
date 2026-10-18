package io.asv.mtgocr.ocrreader

/** Fast-only: scores belong to identities; printing preference never changes the evidence. */
internal object EdScanFastIdentity {
    data class Candidate(val id:String, val score:Float, val identity:String?,
                         val set:String="", val released:String="", val price:Double?=null)
    data class Evidence(val identity:String?, val score:Float?, val runner:Float?,
                        val printing:String?, val alternatives:List<String>)
    fun resolve(input:List<Candidate>):Evidence {
        if(input.any { !it.score.isFinite() }) return Evidence(null,null,null,null,emptyList())
        val hits=input.sortedByDescending { it.score }
        val top=hits.firstOrNull() ?: return Evidence(null,null,null,null,emptyList())
        val key=top.identity?.takeIf { it.isNotBlank() }
        // Unknown identities remain competitors, never silently discarded or merged.
        val runner=hits.firstOrNull { key==null || it.identity!=key }?.score
        if(key==null) return Evidence(null,top.score,runner,top.id,emptyList())
        val tied=hits.filter { it.identity==key && top.score-it.score<TripleScanPolicy.VISUAL_MIN_MARGIN }
        val ordered=tied.sortedWith(compareBy<Candidate> { printingPreference(it.set) }
            .thenByDescending { it.released }.thenBy { it.price?.takeIf { p -> p.isFinite() && p>=0 } ?: Double.POSITIVE_INFINITY }
            .thenBy { it.id })
        // No distinct runner in the truncated top-eight is NOT evidence of a safe margin.
        return Evidence(key,top.score,runner,ordered.firstOrNull()?.id ?: top.id,ordered.map { it.id })
    }

    /** Preference only, never evidence: do not let recent collectible replicas win an art tie.
     * Commander, Masters and bonus sheets are NOT replicas and retain normal date ordering.
     * Unknown sets retain normal priority; absent prices are never interpreted as zero.
     */
    private fun printingPreference(set:String):Int = when(set.lowercase(java.util.Locale.ROOT)) {
        "plst" -> 2
        "30a", "ced", "cei", "sum", "lea", "leb" -> 1
        else -> 0
    }
}
