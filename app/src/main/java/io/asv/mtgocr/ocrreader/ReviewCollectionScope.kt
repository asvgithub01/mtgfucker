package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo

/** Scope inherited from Biblio; applied before tabs, counters and batch operations. */
internal object ReviewCollectionScope {
    fun includes(card: CardInfo, group: String?): Boolean =
        group.isNullOrBlank() || card.groups.contains(group)
}
