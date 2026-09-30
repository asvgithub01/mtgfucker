package io.asv.mtgocr.ocrreader

object ReviewLanguagePolicy {
    // Provisional visual evidence does not mean that no concrete printing is selected.
    fun hasSelectedPrinting(uuid: String?, set: String?, number: String?): Boolean =
        !uuid.isNullOrBlank() && !set.isNullOrBlank() && !number.isNullOrBlank()

    fun proposedLanguage(saved: String, proposals: List<String>, identityConflict: Boolean): String? =
        if(saved.isNotBlank() || identityConflict) null
        else proposals.filter { it.isNotBlank() }.distinct().singleOrNull()

    /** Unknown physical language stays unknown. Otherwise preserve it, then EN, then a stable available language. */
    fun forEdition(current: String, available: List<String>): String? {
        val codes=available.filter { it.isNotBlank() }.distinct().sorted()
        if(codes.isEmpty()) return null
        if(current.isBlank()) return ""
        return current.takeIf { it in codes } ?: codes.firstOrNull { it=="en" } ?: codes.first()
    }
}
