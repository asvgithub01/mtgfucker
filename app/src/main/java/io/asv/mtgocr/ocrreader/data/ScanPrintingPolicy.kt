package io.asv.mtgocr.ocrreader.data

import java.util.Locale

/** Default printing policy for uninterrupted scanning sessions. */
object ScanPrintingPolicy {
    @JvmStatic
    fun preferred(options: List<CardEditionOption>): CardEditionOption? =
        options.firstOrNull { !it.isFoil } ?: options.firstOrNull()

    @JvmStatic
    fun preferred(options: List<CardEditionOption>, preferFoil: Boolean): CardEditionOption? {
        if (!preferFoil) return preferred(options)
        return options.firstOrNull { it.finish.equals("foil", ignoreCase = true) }
            ?: options.firstOrNull { it.isFoil }
            ?: options.firstOrNull()
    }

    /** Returns immediately usable metadata only when the eligible catalog has one printing UUID. */
    fun singleEligible(
        options: List<CardEditionOption>,
        lockedSetCodes: Set<String>,
        preferFoil: Boolean
    ): CardEditionOption? {
        val normalizedLocks = lockedSetCodes.mapTo(HashSet()) { it.trim().uppercase(Locale.US) }
        val eligible = options.filter {
            normalizedLocks.isEmpty() || it.setCode.uppercase(Locale.US) in normalizedLocks
        }
        val groups = eligible.groupBy(CardEditionOption::printingUuid)
        if (groups.size != 1) return null
        return preferred(groups.values.single(), preferFoil)
    }
}
