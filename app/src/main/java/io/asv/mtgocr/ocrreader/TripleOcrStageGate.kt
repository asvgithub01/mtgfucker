package io.asv.mtgocr.ocrreader

/** Main-thread ownership of one OCR lane. A name lookup cannot hold camera work forever,
 * but timeout must never release an ML model still running or release a newer lane twice.
 */
internal class TripleOcrStageGate(private val release:()->Unit) {
    private var modelsRunning=true
    private var names=0
    private var expired=false
    private var released=false
    fun beginName() { check(!released);names++ }
    fun endName() { check(names>0);names--;finishIfReady() }
    fun endModels() { modelsRunning=false;finishIfReady() }
    fun expire() { expired=true;finishIfReady() }
    private fun finishIfReady() {
        if(!released && !modelsRunning && (names==0 || expired)) {
            released=true;release()
        }
    }
}
