package io.asv.mtgocr.ocrreader

object ReviewLanguagePolicy {
    /** Unknown physical language stays unknown. Otherwise preserve it, then EN, then a stable available language. */
    fun forEdition(current: String, available: List<String>): String? {
        val codes=available.filter { it.isNotBlank() }.distinct().sorted()
        if(codes.isEmpty()) return null
        if(current.isBlank()) return ""
        return current.takeIf { it in codes } ?: codes.firstOrNull { it=="en" } ?: codes.first()
    }
}
