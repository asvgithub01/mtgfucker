package io.asv.mtgocr.ocrreader

internal enum class HashBorderVerdict { WHITE, BLACK, UNRESOLVED }

/** First diagnostic phase: only a clearly white or black physical border is actionable. */
internal object HashBorderPolicy {
    fun verdict(color: CardBorderColor?): HashBorderVerdict = when (color) {
        CardBorderColor.WHITE -> HashBorderVerdict.WHITE
        CardBorderColor.BLACK -> HashBorderVerdict.BLACK
        else -> HashBorderVerdict.UNRESOLVED
    }
}
