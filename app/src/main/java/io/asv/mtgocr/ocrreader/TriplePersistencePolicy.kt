package io.asv.mtgocr.ocrreader

internal object TriplePersistencePolicy {
    fun reviewSource(source:String,provisional:Boolean,conflict:Boolean=false)=
        if(provisional || conflict || source=="OCR") "ocr" else "edscan"
}
