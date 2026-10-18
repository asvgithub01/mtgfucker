package io.asv.mtgocr.ocrreader

/** Snapshot enrichment never authorizes an identity, changes a manual language, or moves a copy. */
internal object TriplePhotoLanguagePolicy {
    fun choose(decision:TripleLanguageFusion.Decision,title:TitleLanguageIndex.Evidence):String? =
        decision.code?.takeIf { !decision.conflict && !title.conflict && (title.candidates.isEmpty() || it in title.candidates) }

    fun canApply(sameRow:Boolean,count:Int,manual:Boolean,language:String?,sameSnapshot:Boolean)=
        sameRow && count>0 && !manual && language.isNullOrBlank() && sameSnapshot
}
