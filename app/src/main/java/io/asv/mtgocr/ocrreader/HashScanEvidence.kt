package io.asv.mtgocr.ocrreader

import java.util.Locale

/** Diagnostic evidence only: a copyright year or reused illustration never proves a printing. */
internal object HashScanEvidence {
    fun nameMatches(candidate: String, names: List<String>): Boolean =
        names.any { it.equals(candidate, ignoreCase = true) }

    fun reliableSymbol(distances: Collection<Double>): Boolean {
        val ranked = distances.filter { it.isFinite() }.sorted()
        // One available template is not enough to establish a winning margin.
        return ranked.size >= 2 && ranked[0] <= .58 && ranked[1] - ranked[0] >= .035
    }

    fun resolveEdition(
        candidateName: String,
        ocrNames: List<String>,
        printing: PrintingMetadataGuess?,
        editions: List<HashScanAnalysis.Edition>
    ): HashScanAnalysis.Edition? {
        if (!nameMatches(candidateName, ocrNames)) return null
        val collector = printing?.collectorNumber ?: return null
        // Only the parser's preferred known code may authorize an automatic resolution.
        // Lower-ranked OCR tokens remain diagnostics, not alternative proofs.
        val setCode = printing.setCode?.uppercase(Locale.ROOT) ?: return null
        return editions.asSequence()
            .filter { it.code.uppercase(Locale.ROOT) == setCode }
            .filter { PrintingMetadataParser.collectorKeysMatch(it.collectorNumber, collector) }
            .distinctBy { it.printingUuid }
            .singleOrNull()
    }
}
