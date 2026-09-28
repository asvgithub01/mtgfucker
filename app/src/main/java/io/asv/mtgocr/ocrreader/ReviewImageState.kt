package io.asv.mtgocr.ocrreader

/** A missing URL is not evidence that an image download is running. */
object ReviewImageState {
    enum class Missing { ANALYSIS_PENDING, CATALOG_PENDING, CATALOG_ERROR, NO_IDENTIFICATION, NO_IMAGE }
    fun missing(visual: Boolean, state: String, candidateId: String, candidateLabel: String, catalogError: Boolean): Missing = when {
        catalogError -> Missing.CATALOG_ERROR
        candidateId.isNotBlank() && candidateLabel.isBlank() -> Missing.CATALOG_PENDING
        state == "pending" -> Missing.ANALYSIS_PENDING
        visual && candidateId.isBlank() -> Missing.NO_IDENTIFICATION
        else -> Missing.NO_IMAGE
    }
}
