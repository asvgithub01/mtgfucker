package io.asv.mtgocr.ocrreader

/** Opt-in heuristic, NOT a confirmed edition or a calibrated probability. */
internal object HashProbableEditionPolicy {
    fun choose(
        variants: List<ArtPrintingIndex.Variant>, printing: PrintingMetadataGuess?,
        confirmedSymbol: String?, symbolDistances: Map<String, Double>,
        indexedSet: String, indexedCollector: String, language: String
    ): ArtPrintingIndex.Variant? {
        if (printing?.setCode != null && confirmedSymbol != null &&
            !printing.setCode.equals(confirmedSymbol, true)) return null
        val code = printing?.setCode ?: confirmedSymbol
        val candidates = variants.filter {
            HashPrintingVariantPolicy.canPreselect(it.set.code) &&
                (code == null || it.set.code.equals(code, true)) &&
                (printing?.collectorNumber == null || PrintingMetadataParser.collectorKeysMatch(it.collectorNumber, printing.collectorNumber))
        }
        // Finish/language rows are not independent evidence for an edition.
        val printings = candidates.map { it.set.code }.distinct().flatMap {
            HashEditionResolutionPolicy.printings(candidates, it, language)
        }
        fun distance(v: ArtPrintingIndex.Variant): Double = symbolDistances.entries
            .firstOrNull { it.key.equals(v.set.code, true) }?.value
            ?.takeIf { it.isFinite() && it in 0.0..0.4 } ?: 1.0
        return printings.sortedWith(
            compareByDescending<ArtPrintingIndex.Variant> { printing?.printingYear != null && it.set.releaseDate.take(4).toIntOrNull() == printing.printingYear }
                .thenBy { distance(it) }
                .thenByDescending { it.set.code.equals(indexedSet, true) && PrintingMetadataParser.collectorKeysMatch(it.collectorNumber, indexedCollector) }
                .thenByDescending { it.set.code.equals(indexedSet, true) }
                .thenByDescending { it.set.releaseDate }
                .thenBy { it.set.code }.thenBy { it.collectorNumber }.thenBy { it.printingUuid }
        ).firstOrNull()
    }
}
