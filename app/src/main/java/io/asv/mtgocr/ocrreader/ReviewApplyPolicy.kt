package io.asv.mtgocr.ocrreader

object ReviewApplyPolicy {
    fun applicable(entry: ScanReviewStore.Entry): Boolean =
        entry.state in setOf("conflict", "suggestion") &&
            if (entry.visual) entry.candidateId.isNotBlank() && entry.candidateLabel.isNotBlank()
            else !entry.language.isNullOrBlank() && entry.language != entry.card.languageCode
    /** Never bulk-choose between contradictory photographs of the same inventory row. */
    fun bulkChoices(entries: List<ScanReviewStore.Entry>, selected: Collection<ScanReviewStore.Entry>): List<ScanReviewStore.Entry> =
        entries.filter(::applicable).groupBy { it.card.collectionItemId }.values.mapNotNull { group ->
            group.firstOrNull { candidate -> selected.any { sameProposal(candidate,it) } }
                ?: group.firstOrNull()?.takeIf { group.map { it.visual to (if(it.visual) it.candidateId else it.language) }.distinct().size==1 }
        }

    fun sameProposal(a: ScanReviewStore.Entry, b: ScanReviewStore.Entry): Boolean =
        a.key==b.key && a.source==b.source && a.visual==b.visual && a.stamp==b.stamp && a.candidateId==b.candidateId &&
            a.language==b.language && a.card.collectionItemId==b.card.collectionItemId &&
            a.card.printingUuid==b.card.printingUuid && a.card.finish==b.card.finish && a.card.languageCode==b.card.languageCode
}
