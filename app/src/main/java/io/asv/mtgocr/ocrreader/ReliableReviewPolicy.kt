package io.asv.mtgocr.ocrreader

/** EdScan confirms its stored printing with OCR; OCR-origin scans also require verified art. */
object ReliableReviewPolicy {
    const val VERSION = 2
    fun requiresArt(source: String): Boolean = source == "ocr"

    fun edscanLanguage(canonicalMatch: Boolean, matched: Set<String>, available: List<String>, detected: String?): String? {
        if(!canonicalMatch && matched.isEmpty()) return null
        return resolveLanguage(available,matched,detected)
    }
    fun artReliable(score: Double?, margin: Double?): Boolean =
        score!=null && margin!=null && score.isFinite() && margin.isFinite() && score>=.70 && margin>=.08

    fun sameIdentity(ocrName: String, artName: String): Boolean =
        ocrName.isNotBlank() && ocrName.trim().equals(artName.trim(),true)

    fun language(available: List<String>, strongOcr: String?): String? {
        val codes=available.filter { it.isNotBlank() }.distinct()
        if(strongOcr!=null && strongOcr !in codes) return null
        return if(codes.size==1) codes.single() else strongOcr?.takeIf { it in codes }
    }

    data class LocalTitle(val language: String, val name: String)
    private fun normalized(value: String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    fun titleLanguages(lines: List<String>, titles: List<LocalTitle>): Set<String> {
        val observed=lines.map(::normalized).filter { it.isNotBlank() }.toSet()
        return titles.filter { normalized(it.name).isNotBlank() && normalized(it.name) in observed }.map { it.language }.toSet()
    }
    fun resolveLanguage(available: List<String>, matched: Set<String>, detected: String?): String? {
        if(detected!=null && detected !in available) return null
        if(matched.isNotEmpty()) {
            if(detected!=null) return detected.takeIf { it in matched }
            return matched.singleOrNull()
        }
        return language(available,detected)
    }
}
