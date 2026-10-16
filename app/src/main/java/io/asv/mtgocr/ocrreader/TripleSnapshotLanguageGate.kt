package io.asv.mtgocr.ocrreader

/** Main-thread join of name + language from ONE capture, regardless of callback order. */
internal class TripleSnapshotLanguageGate<T:Any>(private val deliver:(String,Long,T)->Unit) {
    private var identity:Pair<String,Long>?=null
    private var evidence:T?=null
    private var delivered=false
    fun identify(name:String,event:Long) { if(!delivered) { identity=name to event;flush() } }
    fun language(value:T) { if(!delivered) { evidence=value;flush() } }
    private fun flush() {
        val id=identity ?: return
        val value=evidence ?: return
        if(delivered) return
        delivered=true
        deliver(id.first,id.second,value) // recipient still validates generation/event/age
    }
}
