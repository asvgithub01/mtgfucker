package io.asv.mtgocr.ocrreader

/** Diagnostic evidence only: a copyright year or reused illustration never proves a printing. */
internal object HashScanEvidence {
    fun nameMatches(candidate: String, names: List<String>): Boolean =
        names.any { it.equals(candidate, ignoreCase = true) }

    fun reliableSymbol(distances: Collection<Double>): Boolean {
        val ranked = distances.filter { it.isFinite() }.sorted()
        // One available template is not enough to establish a winning margin.
        return ranked.size >= 2 && ranked[0] <= .58 && ranked[1] - ranked[0] >= .035
    }
}
