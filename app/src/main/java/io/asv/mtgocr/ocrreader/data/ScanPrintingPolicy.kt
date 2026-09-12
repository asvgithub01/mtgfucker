package io.asv.mtgocr.ocrreader.data

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
}
