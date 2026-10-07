package io.asv.mtgocr.ocrreader
object TripleArRenderPolicy {
    const val MAX_VISIBLE=20
    fun <T> visible(tracked:List<T>,multiple:Boolean)=tracked.take(if(multiple) MAX_VISIBLE else 1)
}
