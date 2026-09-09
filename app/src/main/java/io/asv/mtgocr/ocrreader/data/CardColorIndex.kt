package io.asv.mtgocr.ocrreader.data

import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Compact offline color lookup used only as a conservative OCR disambiguation signal. */
internal class CardColorIndex private constructor(
    private val colorsByCanonicalName: Map<String, String>
) {
    fun isReliableObservedColor(color: String?): Boolean = color == WHITE || color == BLUE

    /** Unknown, colorless and multicolored cards remain neutral instead of causing a rejection. */
    fun isCompatible(canonicalName: String, observedColor: String?): Boolean {
        if (!isReliableObservedColor(observedColor)) return true
        val expected = colorsByCanonicalName[canonicalName] ?: return true
        return expected.length != 1 || expected == observedColor
    }

    companion object {
        const val WHITE = "W"
        const val BLUE = "U"

        fun read(compressed: InputStream): CardColorIndex {
            val colors = HashMap<String, String>(45_000)
            GZIPInputStream(compressed).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val separator = line.lastIndexOf('\t')
                    if (separator <= 0 || separator == line.lastIndex) return@forEach
                    colors[line.substring(0, separator)] = line.substring(separator + 1)
                }
            }
            return CardColorIndex(colors)
        }

        fun from(colors: Map<String, String>): CardColorIndex = CardColorIndex(colors)
    }
}
