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

    /** Cheapest known price among the five newest sets, never an expensive historical default. */
    fun recentAffordable(options: List<CardEditionOption>, preferFoil: Boolean,
        amount: (CardEditionOption) -> Double? = { it.price }): CardEditionOption? {
        val finish = if (preferFoil) "foil" else "nonfoil"
        val eligible = options.filter { it.finish == finish }
        if (eligible.isEmpty()) return null
        val recentSets = eligible.sortedWith(compareByDescending<CardEditionOption> { it.releaseDate }
            .thenBy { it.setCode }).map { it.setCode }.distinct().take(5).toSet()
        val recent = eligible.filter { it.setCode in recentSets }
        val priced = recent.filter { amount(it)?.let { value -> value.isFinite() && value >= 0 } == true }
        return if (priced.isEmpty()) recent.sortedWith(compareByDescending<CardEditionOption> { it.releaseDate }
            .thenBy { it.printingUuid }).firstOrNull()
        else priced.sortedWith(compareBy<CardEditionOption> { amount(it) }
            .thenByDescending { it.releaseDate }.thenBy { it.printingUuid }).firstOrNull()
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
