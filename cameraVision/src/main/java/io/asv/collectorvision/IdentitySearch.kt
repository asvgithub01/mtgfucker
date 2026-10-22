package io.asv.collectorvision

/** Full-catalog identity ranking, opt-in for Fast. Printing duplicates cannot hide a rival. */
class IdentitySearch(private val ids:List<String>, private val keys:Map<String,String>) {
    private val groups: IntArray
    private val groupCount: Int
    init {
        val names=linkedMapOf<String,Int>()
        groups=IntArray(ids.size) { i -> names.getOrPut(key(ids[i])) { names.size } }
        groupCount=names.size
    }
    private fun key(id:String)=keys[id]?.takeIf { it.isNotBlank() } ?: "unknown:$id"
    fun select(scores:FloatArray):List<Hit> {
        require(scores.size==ids.size)
        val best=IntArray(groupCount) { -1 }
        var winner=-1
        for(i in scores.indices) if(scores[i].isFinite()) {
            val g=groups[i];val old=best[g]
            if(old<0 || scores[i]>scores[old]) best[g]=i
            if(winner<0 || scores[i]>scores[winner]) winner=i
        }
        if(winner<0) return emptyList()
        // Keep only eight maxima without sorting/boxing tens of thousands of identities.
        fun insert(top:IntArray,row:Int) {
            for(k in top.indices) if(top[k]<0 || scores[row]>scores[top[k]]) {
                for(j in top.lastIndex downTo k+1) top[j]=top[j-1]
                top[k]=row;break
            }
        }
        val rivalRows=IntArray(8) { -1 };val printingRows=IntArray(8) { -1 }
        for(row in best) if(row>=0 && groups[row]!=groups[winner]) insert(rivalRows,row)
        for(row in scores.indices) if(groups[row]==groups[winner] && scores[row].isFinite()) insert(printingRows,row)
        val rivals=rivalRows.filter { it>=0 };val printings=printingRows.filter { it>=0 }
        return (printings+rivals).sortedByDescending { scores[it] }.map { Hit(ids[it],scores[it]) }
    }
    fun merge(hits:List<Hit>):List<Hit> {
        val ordered=hits.filter { it.score.isFinite() }.groupBy { it.cardId }.map { it.value.maxBy { h->h.score } }.sortedByDescending { it.score }
        val winner=ordered.firstOrNull()?.let { key(it.cardId) } ?: return emptyList()
        val printing=ordered.filter { key(it.cardId)==winner }.take(8)
        val rivals=ordered.filter { key(it.cardId)!=winner }.distinctBy { key(it.cardId) }.take(8)
        return (printing+rivals).sortedByDescending { it.score }
    }
    fun strong(hits:List<Hit>):Boolean {
        val top=hits.firstOrNull() ?: return false
        val rival=rival(hits) ?: return false
        return top.score>=.70f && top.score-rival.score>=.08f
    }
    fun rival(hits:List<Hit>):Hit? = hits.firstOrNull()?.let { top -> hits.firstOrNull { key(it.cardId)!=key(top.cardId) } }
    fun sameIdentity(a:List<Hit>,b:List<Hit>)=a.isNotEmpty() && b.isNotEmpty() && key(a.first().cardId)==key(b.first().cardId)
}
