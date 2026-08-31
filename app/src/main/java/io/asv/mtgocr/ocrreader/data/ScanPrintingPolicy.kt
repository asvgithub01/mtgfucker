package io.asv.mtgocr.ocrreader.data

/** Default printing policy for uninterrupted scanning sessions. */
object ScanPrintingPolicy {
    @JvmStatic
    fun preferred(options: List<CardEditionOption>): CardEditionOption? =
        options.firstOrNull { !it.isFoil } ?: options.firstOrNull()
}
