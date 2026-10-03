package io.asv.mtgocr.ocrreader

/** Source determines analysis scope; explicit reanalysis never reuses an old OCR result. */
object ReviewAnalysisPolicy {
    data class Plan(val languageOnly: Boolean, val copyOriginalOcr: Boolean)
    fun plan(source: String, reliableSave: Boolean, cached: Boolean, originalOcrValid: Boolean): Plan {
        val languageOnly=!ReliableReviewPolicy.requiresArt(source)
        return Plan(languageOnly,reliableSave && languageOnly && !cached && originalOcrValid)
    }

    /** Old full reanalyses must not reintroduce artwork proposals for an EdScan printing. */
    fun evidenceAttempts(source: String, attempt: String?, both: Boolean): List<String> = when {
        !ReliableReviewPolicy.requiresArt(source) -> if(attempt!=null && both) listOf("language:$attempt") else listOf("")
        attempt!=null && both -> listOf("language:$attempt",attempt)
        attempt!=null -> listOf(attempt)
        else -> listOf("")
    }
}
