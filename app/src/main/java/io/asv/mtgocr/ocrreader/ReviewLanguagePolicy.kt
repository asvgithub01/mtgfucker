package io.asv.mtgocr.ocrreader

object ReviewLanguagePolicy {
    // Provisional visual evidence does not mean that no concrete printing is selected.
    fun hasSelectedPrinting(uuid: String?, set: String?, number: String?): Boolean =
        !uuid.isNullOrBlank() && !set.isNullOrBlank() && !number.isNullOrBlank()

    fun proposedLanguage(saved: String, proposals: List<String>, identityConflict: Boolean): String? =
        if(saved.isNotBlank() || identityConflict) null
        else proposals.filter { it.isNotBlank() }.distinct().singleOrNull()

    /** Edition-picker draft only: preserve a valid choice, otherwise unique language or supported OCR proposal. */
    fun forSelectedEdition(current: String, available: List<String>, proposals: List<String>): String {
        val codes=available.filter { it.isNotBlank() }.distinct()
        if(current.isNotBlank() && current in codes) return current
        if(codes.size==1) return codes.single()
        val proposed=proposals.filter { it.isNotBlank() }.distinct().singleOrNull()
        return proposed?.takeIf { it in codes }.orEmpty()
    }

    /** Unknown physical language stays unknown. Otherwise preserve it, then EN, then a stable available language. */
    fun forEdition(current: String, available: List<String>): String? {
        val codes=available.filter { it.isNotBlank() }.distinct().sorted()
        if(codes.isEmpty()) return null
        if(current.isBlank()) return ""
        return current.takeIf { it in codes } ?: codes.firstOrNull { it=="en" } ?: codes.first()
    }
}
