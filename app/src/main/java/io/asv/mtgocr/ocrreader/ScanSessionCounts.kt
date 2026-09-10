package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo

/** Session counters always represent physical copies, never serialized rows or selected IDs. */
object ScanSessionCounts {
    @JvmStatic
    fun cardsInGroup(cards: List<CardInfo>, groupName: String): List<CardInfo> =
        if (groupName.isBlank()) emptyList() else cards.filter { groupName in it.groups }

    @JvmStatic
    fun total(cards: List<CardInfo>): Int = cards.sumOf { it.quantityCount }

    @JvmStatic
    fun selected(cards: List<CardInfo>, selectedIds: Set<String>): Int = cards.sumOf { card ->
        if (card.collectionItemId in selectedIds) card.quantityCount else 0
    }

    @JvmStatic
    fun range(cards: List<CardInfo>, chronologicalIndex: Int): IntRange {
        val first = 1 + cards.take(chronologicalIndex.coerceAtLeast(0)).sumOf { it.quantityCount }
        val quantity = cards.getOrNull(chronologicalIndex)?.quantityCount ?: 1
        return first..(first + quantity - 1)
    }
}
