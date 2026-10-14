package io.asv.mtgocr.ocrreader

/** Experimental SHADOW policy. Scores are similarities, not calibrated probabilities.
 * Never authorizes a collection write. Each row is an independent sufficient condition;
 * their union is monotonic in score, margin and independent observations.
 */
internal class TripleAdaptiveVisualPolicy {
    data class Band(val score:Float,val margin:Float,val frames:Int)
    data class Reading(val candidate:Boolean,val hypothetical:Boolean,val counts:List<Int>,val reason:String)
    private var identity=""
    private var last=-1L
    private var lastQualified=-1L
    private val samples=List(BANDS.size) { java.util.ArrayDeque<Long>() }
    fun reset() { identity="";last=-1;lastQualified=-1;samples.forEach { it.clear() } }
    fun observe(id:String?,score:Float?,runner:Float?,at:Long,comparisonComplete:Boolean):Reading {
        fun result(reason:String,candidate:Boolean=false)=Reading(candidate && comparisonComplete,candidate,samples.map { it.size },reason)
        if(at<=last) return result("out_of_order")
        if(id!=identity || lastQualified<0 || at-lastQualified>6000) {
            samples.forEach { it.clear() };identity=id.orEmpty();lastQualified=-1
        }
        last=at
        samples.forEach { votes -> while(votes.isNotEmpty() && at-votes.first>12000) votes.removeFirst() }
        if(id.isNullOrBlank() || score==null || !score.isFinite()) return result("no_candidate")
        if(runner==null || !runner.isFinite()) return result("no_runner")
        var qualifies=false
        for(i in BANDS.indices) {
            val band=BANDS[i]
            if(score>=band.score && score-runner>=band.margin) {
                samples[i].addLast(at)
                if(samples[i].size>band.frames) samples[i].removeFirst()
                qualifies=true
            }
        }
        // Weak samples are neutral, not extra votes and not a reset of another engine.
        if(qualifies) lastQualified=at
        val candidate=qualifies && BANDS.indices.any { samples[it].size>=BANDS[it].frames &&
            (BANDS[it].frames==1 || at-samples[it].first>=600) &&
            score>=BANDS[it].score && score-runner>=BANDS[it].margin }
        return result(if(!comparisonComplete) "incomplete_comparison" else if(candidate) "shadow_candidate" else if(qualifies) "readings" else "weak",candidate)
    }
    companion object {
        val BANDS=listOf(Band(.55f,.30f,5),Band(.60f,.20f,3),Band(.70f,.08f,2),Band(.85f,.25f,1))
    }
}
