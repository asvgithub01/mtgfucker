package io.asv.mtgocr.ocrreader

import android.content.Context
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.util.IdentityHashMap

/** Builds a display-only order without mutating the chronological session used by # ordinals. */
object ScanSessionSort {
    const val ENTRY = 0
    const val PRICE_DESCENDING = 1
    const val INCOMPLETE_FIRST = 2
    const val ALPHABETICAL = 3

    @JvmStatic
    fun sorted(context: Context, cards: List<CardInfo>, mode: Int): List<CardInfo> = sorted(
        cards,
        mode,
        priceOf = { PriceCurrency.amountOrNull(context, it) },
        incomplete = { !isComplete(it) }
    )

    internal fun sorted(
        cards: List<CardInfo>,
        mode: Int,
        priceOf: (CardInfo) -> Double? = { null },
        incomplete: (CardInfo) -> Boolean = { !isComplete(it) }
    ): List<CardInfo> {
        val chronologicalIndex = IdentityHashMap<CardInfo, Int>()
        cards.forEachIndexed { index, card -> chronologicalIndex[card] = index }
        fun newestFirst(left: CardInfo, right: CardInfo): Int =
            (chronologicalIndex[right] ?: 0).compareTo(chronologicalIndex[left] ?: 0)

        val comparator = when (mode) {
            PRICE_DESCENDING -> Comparator<CardInfo> { left, right ->
                val leftPrice = priceOf(left)
                val rightPrice = priceOf(right)
                when {
                    leftPrice == null && rightPrice == null -> newestFirst(left, right)
                    leftPrice == null -> 1
                    rightPrice == null -> -1
                    else -> rightPrice.compareTo(leftPrice).takeIf { it != 0 }
                        ?: newestFirst(left, right)
                }
            }
            INCOMPLETE_FIRST -> Comparator<CardInfo> { left, right ->
                val byCompleteness = when {
                    incomplete(left) && !incomplete(right) -> -1
                    !incomplete(left) && incomplete(right) -> 1
                    else -> 0
                }
                byCompleteness.takeIf { it != 0 } ?: newestFirst(left, right)
            }
            ALPHABETICAL -> Comparator<CardInfo> { left, right ->
                left.name.orEmpty().compareTo(right.name.orEmpty(), ignoreCase = true)
                    .takeIf { it != 0 } ?: newestFirst(left, right)
            }
            else -> Comparator(::newestFirst)
        }
        return cards.sortedWith(comparator)
    }

    @JvmStatic
    fun isComplete(card: CardInfo): Boolean =
        PriceCurrency.hasAmount(card) &&
            card.imgPath.orEmpty().isNotBlank() &&
            card.printingUuid.orEmpty().isNotBlank() &&
            card.setCode.orEmpty().isNotBlank() &&
            card.collectorNumber.orEmpty().isNotBlank() &&
            card.description.orEmpty().isNotBlank()
}
