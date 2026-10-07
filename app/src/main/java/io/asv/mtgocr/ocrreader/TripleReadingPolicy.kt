package io.asv.mtgocr.ocrreader

/** Printing hypotheses are metadata, not physical-copy identifiers. */
internal object TripleReadingPolicy {
    fun identity(canonicalName:String)=TitleLanguageIndex.normalize(canonicalName)
    fun keepCurrent(manual:Boolean, currentPrinting:String, currentProvisional:Boolean,
                    incomingPrinting:String, incomingProvisional:Boolean):Boolean =
        manual || (currentPrinting.isNotBlank() && (incomingPrinting.isBlank() || (incomingProvisional && (!currentProvisional || currentPrinting!=incomingPrinting))))
    fun titleLanguage(raw:TitleLanguageIndex.Evidence, alias:TitleLanguageIndex.Evidence, hint:String?):String? {
        if(raw.conflict) return null
        if(raw.matches.isNotEmpty()) return raw.candidates.singleOrNull()
        val code=alias.candidates.singleOrNull() ?: return null
        return code.takeIf { hint.isNullOrBlank() || hint==it }
    }
}
