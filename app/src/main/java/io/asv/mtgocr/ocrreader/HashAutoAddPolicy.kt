package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardEditionOption

internal data class HashAutoAddTarget(
    val cardName: String,
    val printingUuid: String?,
    val setCode: String,
    val collectorNumber: String,
    val scryfallId: String? = null
)

/** Resolves the top hash hit to the exact indexed printing selected by the opt-in checkbox. */
internal object HashAutoAddPolicy {
    fun hasResolvedPrinting(
        indexedVariants: Int,
        compatibleVariants: Int,
        hasResolvedVariant: Boolean,
        hasExactOcrEdition: Boolean
    ): Boolean = indexedVariants == 0 || (
        compatibleVariants > 0 && (hasResolvedVariant || hasExactOcrEdition)
    )

    fun acceptsTopHit(phashDistance: Int, hasExactEdition: Boolean): Boolean =
        hasExactEdition || phashDistance <= MAX_UNVERIFIED_PHASH_DISTANCE

    fun preferredOption(
        target: HashAutoAddTarget,
        options: List<CardEditionOption>
    ): CardEditionOption? {
        val matching = options.asSequence().filter { option ->
            if (!target.printingUuid.isNullOrBlank()) {
                option.printingUuid == target.printingUuid
            } else {
                option.setCode.equals(target.setCode, ignoreCase = true) &&
                    PrintingMetadataParser.collectorKeysMatch(
                        option.collectorNumber,
                        target.collectorNumber
                    )
            }
        }
        return matching.sortedWith(
            compareBy<CardEditionOption> { CardFinish.isFoil(it.finish) }
                .thenBy { it.finish }
        ).firstOrNull()
    }

    private const val MAX_UNVERIFIED_PHASH_DISTANCE = 10
}
