package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardEditionOption

internal data class HashOcrConflictState(
    val name: String = "",
    val reads: Int = 0
)

internal object HashOcrFallbackPolicy {
    private const val REQUIRED_READS = 2

    fun observe(
        previous: HashOcrConflictState,
        names: List<String>
    ): HashOcrConflictState {
        if (names.isEmpty()) return HashOcrConflictState()
        val repeated = names.firstOrNull {
            HashScanEvidence.namesEquivalent(previous.name, it)
        }
        return if (repeated != null) {
            HashOcrConflictState(previous.name, previous.reads + 1)
        } else {
            HashOcrConflictState(names.first(), 1)
        }
    }

    fun confirmedName(state: HashOcrConflictState): String? =
        state.name.takeIf { state.reads >= REQUIRED_READS }

    fun preferredPrinting(
        options: List<CardEditionOption>,
        printing: PrintingMetadataGuess?
    ): CardEditionOption? {
        val safe = options.filter { HashPrintingVariantPolicy.canPreselect(it.setCode) }
        val exact = if (printing?.setCode != null && printing.collectorNumber != null) {
            safe.filter {
                it.setCode.equals(printing.setCode, ignoreCase = true) &&
                    PrintingMetadataParser.collectorKeysMatch(
                        it.collectorNumber,
                        printing.collectorNumber
                    )
            }
        } else {
            emptyList()
        }
        return (exact.ifEmpty { safe }).minWithOrNull(
            compareBy<CardEditionOption> { it.isFoil }
                .thenByDescending { it.releaseDate }
                .thenBy { it.printingUuid }
        )
    }
}
