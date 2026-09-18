package io.asv.mtgocr.ocrreader

import java.util.Locale

/** Chronicles retained the original expansion symbol on its white-bordered reprints. */
internal object ChroniclesSymbolPolicy {
    private const val CHRONICLES = "CHR"
    private const val CHRONICLES_YEAR = 1995
    private val retainedSymbolSets = setOf("ARN", "ATQ", "LEG", "DRK")

    fun applies(
        detectedSetCodes: Collection<String>,
        printingYear: Int?,
        border: CardBorderColor
    ): Boolean {
        if (border != CardBorderColor.WHITE) return false
        val normalized = detectedSetCodes.mapTo(HashSet()) { it.trim().uppercase(Locale.US) }
        return normalized.any { it in retainedSymbolSets } ||
            (printingYear == CHRONICLES_YEAR && normalized.isNotEmpty())
    }

    fun visualSetCodes(
        detectedSetCodes: Set<String>,
        printingYear: Int?,
        border: CardBorderColor
    ): Set<String> = buildSet {
        detectedSetCodes.mapTo(this) { it.trim().uppercase(Locale.US) }
        if (applies(detectedSetCodes, printingYear, border)) add(CHRONICLES)
    }

    fun isChronicles(setCode: String): Boolean =
        setCode.trim().uppercase(Locale.US) == CHRONICLES

    fun hasRetainedSymbolEvidence(detectedSetCodes: Collection<String>): Boolean =
        detectedSetCodes.any { isRetainedSymbolSet(it) }

    fun isRetainedSymbolSet(setCode: String): Boolean =
        setCode.trim().uppercase(Locale.US) in retainedSymbolSets
}
