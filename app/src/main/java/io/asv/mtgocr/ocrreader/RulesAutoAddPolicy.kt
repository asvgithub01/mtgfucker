package io.asv.mtgocr.ocrreader

/** Operational catalogue inference, not a claim that artwork proves every physical attribute. */
internal object RulesAutoAddPolicy {
    data class Artwork(val id: String?, val name: String, val phash: Int, val dhash: Int,
        val variants: List<ArtPrintingIndex.Variant>)
    data class Decision(val reason: String, val variant: ArtPrintingIndex.Variant? = null,
        val finish: String = "", val artworkId: String? = null) {
        val canAutoAdd: Boolean get() = variant != null
    }

    fun evaluate(art: List<Artwork>, names: List<String>, evidence: RulesScanPolicy.Decision,
        footer: StructuredPrintingRead, enabled: Boolean, preferredFinish: String,
        hasErrors: Boolean = false, hasPhoto: Boolean = true,
        setTypes: Map<String, String> = emptyMap()): Decision {
        fun blocked(reason: String) = Decision(reason)
        if (!enabled) return blocked("DISABLED")
        if (hasErrors || !hasPhoto) return blocked("INCOMPLETE_CAPTURE")
        if (evidence.status == RulesScanPolicy.Status.CONFLICT || footer.state == ScanReadState.CONFLICT)
            return blocked("CONFLICT")
        val artReason = RulesArtworkEvidence.reason(art, names)
        if (artReason != "STRONG_ART") return blocked(artReason)
        val top = art.first()
        // Never manufacture uniqueness by first deleting other sets, languages or finishes.
        val catalogue = top.variants
        if (catalogue.isEmpty()) return blocked("NO_ART_CATALOGUE")
        val codes = catalogue.map { it.set.code.uppercase(java.util.Locale.ROOT) }.distinct()
        var variants = catalogue
        var reason = "CATALOG_UNIQUE_ARTWORK"
        val types = setTypes.mapKeys { it.key.uppercase(java.util.Locale.ROOT) }
        // These families can keep the ORIGINAL footer. Its code must never eliminate them.
        if (codes.any { it in setOf("PLST", "MB1", "FMB1", "MB2") || types[it] == "promo" })
            return blocked("RETAINED_FOOTER_POSSIBLE")
        val uniqueArt = codes.size == 1 && catalogue.map { it.printingUuid }.distinct().size == 1
        if (!uniqueArt) {
            val printed = footer.footer?.takeIf { it.number != null }
                ?: return blocked(if (codes.size != 1) "MULTIPLE_SETS" else "MULTIPLE_PRINTINGS")
            if (!footer.corroboratedTuple) return blocked("FOOTER_NEEDS_CORROBORATION")
            // Other product families need a dedicated printed-profile sidecar before excluding them.
            if (codes.any { types[it] !in setOf("core", "expansion", "masters", "draft_innovation") })
                return blocked("UNSUPPORTED_SET_PROFILE")
            variants = catalogue.filter { it.set.code.equals(printed.setCode, true) &&
                RulesScanPolicy.collectorKey(it.collectorNumber) == RulesScanPolicy.collectorKey(printed.number!!) }
            if (variants.isEmpty()) return blocked("FOOTER_NOT_IN_ART_CATALOGUE")
            if (variants.map { it.printingUuid }.distinct().size != 1) return blocked("MULTIPLE_PRINTINGS")
            reason = "STRUCTURED_FOOTER"
        }
        val set = variants.map { it.set.code.uppercase(java.util.Locale.ROOT) }.distinct().singleOrNull()
            ?: return blocked("MULTIPLE_SETS")
        if (!HashPrintingVariantPolicy.canPreselect(set)) return blocked("HISTORICAL_SET")
        if (variants.map { it.printingUuid }.distinct().size != 1) return blocked("MULTIPLE_PRINTINGS")
        // APV1 uses 255 for a normal single-face card, 0/1 for explicit front/back.
        if (variants.any { !HashScanEvidence.namesEquivalent(it.cardName, top.name) || it.face !in setOf(0, 255) })
            return blocked("UNSUPPORTED_FACE")
        val number = variants.map { RulesScanPolicy.collectorKey(it.collectorNumber) }.distinct().singleOrNull()
            ?: return blocked("INCONSISTENT_CATALOGUE")
        footer.footer?.let { printed ->
            if (!set.equals(printed.setCode, true) ||
                (printed.number != null && number != RulesScanPolicy.collectorKey(printed.number)))
                return blocked("FOOTER_CONFLICT")
        }
        if (evidence.languageState != ScanReadState.READ || evidence.language.isBlank()) return blocked("LANGUAGE_UNKNOWN")
        val localized = variants.filter { it.languageCode == evidence.language }
        if (localized.isEmpty()) return blocked("LANGUAGE_NOT_IN_CATALOGUE")
        val finishBit = when (preferredFinish) {
            "nonfoil" -> ArtPrintingIndex.FINISH_NONFOIL
            "foil" -> ArtPrintingIndex.FINISH_FOIL
            "etched" -> ArtPrintingIndex.FINISH_ETCHED
            else -> return blocked("FINISH_UNKNOWN")
        }
        if (localized.any { it.finishes and finishBit == 0 }) return blocked("FINISH_UNAVAILABLE")
        return Decision(reason, localized.first(), preferredFinish, top.id)
    }
}
