package io.asv.mtgocr.ocrreader

/** A read updates its own event. A different identity or observed absence starts another copy. */
internal class TripleEncounterGate {
    private var identity=""
    private var missingSince:Long?=null
    private var armed=true
    var event=0L; private set
    fun rebindIdentity(id:String) { identity=id }
    fun present() { missingSince=null }
    fun missing(now:Long) {
        if(missingSince==null) missingSince=now
        if(now-requireNotNull(missingSince)>=1000) armed=true
    }
    fun observe(id:String):Long {
        if(armed || identity!=id) { event++;identity=id;armed=false }
        present();return event
    }
}
