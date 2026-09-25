package io.asv.mtgocr.ocrreader

/** Shared visual gate for UI and automatic policy; strong art does not prove an edition. */
internal object RulesArtworkEvidence {
    fun reason(art: List<RulesAutoAddPolicy.Artwork>, names: List<String>): String {
        val top = art.firstOrNull() ?: return "NO_ART"
        if (top.id.isNullOrBlank() || top.phash !in 0..10 || top.dhash !in 0..16) return "WEAK_ART"
        val competitors = art.filter { it.id != top.id }
        if (competitors.isEmpty() || competitors.any { it.phash - top.phash < 4 }) return "ART_MARGIN"
        if (names.any { !HashScanEvidence.namesEquivalent(it, top.name) }) return "OCR_CONFLICT"
        return "STRONG_ART"
    }
}
