package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo

/** Display order only: preserve stored chronology and stable ordering for equal timestamps. */
object CollectionEntryOrder {
    @JvmField
    val NEWEST_FIRST: Comparator<CardInfo> = compareByDescending { it.addedAt }
}
