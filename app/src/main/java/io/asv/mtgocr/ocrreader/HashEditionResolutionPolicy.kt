package io.asv.mtgocr.ocrreader

/** Keep artwork identity, catalogue uniqueness and visual symbol evidence separate. */
internal object HashEditionResolutionPolicy {
    // A unique catalogue entry does not make a weak nearest-neighbour hit a recognized card.
    fun recognizedArtwork(name: String, names: List<String>, phash: Int, dhash: Int): Boolean =
        if (names.isNotEmpty()) HashScanEvidence.nameMatches(name, names)
        else phash <= 10 && dhash <= 16

    fun uniqueArtwork(
        catalogue: List<ArtPrintingIndex.Variant>, compatible: List<ArtPrintingIndex.Variant>,
        printing: PrintingMetadataGuess?, language: String
    ): ArtPrintingIndex.Variant? {
        // Check the complete art catalogue BEFORE OCR/language/border filters remove alternatives.
        val set = catalogue.map { it.set.code.uppercase(java.util.Locale.ROOT) }.distinct().singleOrNull() ?: return null
        if (!HashPrintingVariantPolicy.canPreselect(set)) return null
        if (printing?.setCode != null && !printing.setCode.equals(set, true)) return null
        val candidates = if (printing?.collectorNumber != null) compatible.filter {
            PrintingMetadataParser.collectorKeysMatch(it.collectorNumber, printing.collectorNumber)
        } else compatible
        return printings(candidates, set, language).singleOrNull()
    }

    fun identityVariants(name: String, groups: List<List<ArtPrintingIndex.Variant>>): List<ArtPrintingIndex.Variant> =
        groups.flatten().filter { HashScanEvidence.namesEquivalent(it.cardName, name) }
            .distinctBy { it.printingUuid to it.languageCode }

    fun identityIndex(candidates: List<String>, names: List<String>): Int? {
        val matches = candidates.indices.filter { HashScanEvidence.nameMatches(candidates[it], names) }
        val first = matches.firstOrNull() ?: return null
        return first.takeIf { matches.all { HashScanEvidence.namesEquivalent(candidates[it], candidates[first]) } }
    }

    fun printings(variants: List<ArtPrintingIndex.Variant>, set: String, language: String): List<ArtPrintingIndex.Variant> {
        val candidates = variants.filter { it.set.code.equals(set, true) }
        val normal = candidates.filter { it.finishes and ArtPrintingIndex.FINISH_NONFOIL != 0 }
        return normal.ifEmpty { candidates }.groupBy { it.printingUuid }.values.map { rows ->
            rows.firstOrNull { it.languageCode.equals(language, true) }
                ?: rows.firstOrNull { it.languageCode == "en" } ?: rows.first()
        }.sortedBy { it.collectorNumber }
    }

    fun automatic(
        variants: List<ArtPrintingIndex.Variant>, printing: PrintingMetadataGuess?,
        symbolSet: String?, language: String
    ): ArtPrintingIndex.Variant? {
        val code = printing?.setCode
        // A successful symbol never overrides conflicting physical footer evidence.
        if (code != null && symbolSet != null && !code.equals(symbolSet, true)) return null
        val set = code?.takeIf { printing.collectorNumber != null } ?: symbolSet ?: return null
        if (!HashPrintingVariantPolicy.canPreselect(set)) return null
        val filtered = if (code != null && printing.collectorNumber != null) variants.filter {
            it.set.code.equals(code, true) && PrintingMetadataParser.collectorKeysMatch(it.collectorNumber, printing.collectorNumber)
        } else variants
        return printings(filtered, set, language).singleOrNull()
    }
}
