package io.asv.mtgocr.ocrreader

/** Conservative experimental thresholds, independent of Android for regression tests. */
internal object SetSymbolHashPolicy {
    const val MAX_DISTANCE = .24
    const val MIN_MARGIN = .055

    // Tiny printed silhouettes retain shape better than the high-frequency DCT coefficients.
    fun distance(silhouette: Double, phashBits: Int, aspect: Double): Double =
        .75 * silhouette + .15 * phashBits / 63.0 + .10 * aspect

    /** The List collector embeds the original set, whose glyph is retained on the reprint. */
    fun retainedSymbols(variants: List<ArtPrintingIndex.Variant>): Map<String, List<String>> {
        val listPrintings = variants.filter { it.set.code.equals("PLST", true) }
        if (listPrintings.isEmpty()) return emptyMap()
        val originals = listPrintings.map {
            Regex("^([A-Za-z0-9]{2,8})-.+$").matchEntire(it.collectorNumber)?.groupValues?.get(1)
                ?.uppercase(java.util.Locale.ROOT)
        }
        // An unknown source must not be silently dropped from the comparison.
        return mapOf("PLST" to if (originals.any { it == null }) emptyList() else originals.filterNotNull().distinct())
    }

    fun winner(distances: Map<String, Double>, missingSets: Collection<String>): String? {
        if (missingSets.isNotEmpty() || distances.values.any { !it.isFinite() }) return null
        val ranked = distances.entries.filter { it.value.isFinite() }.sortedBy { it.value }
        // One possible edition still needs an absolute visual match. Reused glyphs remain ambiguous.
        if (ranked.isEmpty() || ranked[0].value > MAX_DISTANCE) return null
        return ranked[0].key.takeIf { ranked.size == 1 || ranked[1].value - ranked[0].value >= MIN_MARGIN }
    }

    fun candidateIndex(names: List<String>, ocrNames: List<String>, ocrEnabled: Boolean): Int? =
        if (ocrEnabled) HashScanEvidence.firstMatchingCandidateIndex(names, ocrNames)
        else names.indices.firstOrNull()

    fun selectVariant(
        variants: List<ArtPrintingIndex.Variant>,
        setCode: String?,
        preferredLanguage: String = "en"
    ): ArtPrintingIndex.Variant? {
        if (setCode == null || !HashPrintingVariantPolicy.canPreselect(setCode)) return null
        val candidates = variants.filter { it.set.code.equals(setCode, true) }
        // Some core sets catalogue the white-border normal and black-border foil separately.
        // Prefer normal, but never use finish to pick arbitrarily between two normal printings.
        val normal = candidates.filter { it.finishes and ArtPrintingIndex.FINISH_NONFOIL != 0 }
        val matches = normal.ifEmpty { candidates }
        // Language rows can share a UUID. A set symbol cannot choose another art/collector.
        if (matches.map { it.printingUuid }.distinct().size != 1) return null
        return matches.firstOrNull { it.languageCode.equals(preferredLanguage, true) }
            ?: matches.firstOrNull { it.languageCode.equals("en", true) }
            ?: matches.firstOrNull()
    }
}
