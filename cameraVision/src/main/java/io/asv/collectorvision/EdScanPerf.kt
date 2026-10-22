package io.asv.collectorvision

import android.os.SystemClock
import android.util.Log

/** Debug-only, aggregated stage timings. No images, card IDs or per-frame disk writes. */
object EdScanPerf {
    @Volatile var enabled=false
    private data class Sample(var count: Int=0,var total: Long=0,var max: Long=0,var since: Long=SystemClock.elapsedRealtime())
    private val samples=mutableMapOf<String,Sample>()
    @Synchronized fun record(stage: String, nanos: Long) {
        if(!enabled) return
        val key=stage+"@"+Thread.currentThread().name
        val s=samples.getOrPut(key) { Sample() }
        s.count++; s.total+=nanos; s.max=maxOf(s.max,nanos)
        val now=SystemClock.elapsedRealtime()
        if(now-s.since>=3000) {
            Log.d("EdScanPerf","$key n=${s.count} windowMs=${now-s.since} meanMs=${s.total/s.count/1_000_000.0} maxMs=${s.max/1_000_000.0}")
            samples.remove(key)
        }
    }
    fun <T> measure(stage: String, action: ()->T): T {
        if(!enabled) return action()
        val start=System.nanoTime()
        try { return action() } finally { record(stage,System.nanoTime()-start) }
    }
    fun <T> once(stage: String, action: ()->T): T {
        if(!enabled) return action()
        val start=System.nanoTime()
        try { return action() } finally { event("$stage ms=${(System.nanoTime()-start)/1_000_000.0}") }
    }
    fun event(message: String) { if(enabled) Log.i("EdScanPerf",message+" thread="+Thread.currentThread().name) }
}
