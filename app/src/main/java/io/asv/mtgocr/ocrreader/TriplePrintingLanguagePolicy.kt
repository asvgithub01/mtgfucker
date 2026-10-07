package io.asv.mtgocr.ocrreader

internal object TriplePrintingLanguagePolicy {
    fun eligible(source:String, provisional:Boolean, printing:String, set:String, number:String)=
        source in setOf("AR","EdScan") && !provisional && printing.isNotBlank() && set.isNotBlank() && number.isNotBlank()
    fun single(languages:List<String>, complete:Boolean):String? =
        if(!complete || languages.isEmpty() || languages.any { it.isBlank() || it=="und" }) null
        else languages.distinct().singleOrNull()
    fun conflicts(expected:String?, observed:String?)=expected!=null && observed!=null && expected!=observed
}
