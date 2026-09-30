package io.asv.mtgocr.ocrreader

object ReviewResolutionPolicy {
    fun hasIdentityConflict(evidence: List<ScanReviewStore.Entry>): Boolean = evidence.any {
        it.card.isProvisionalEdition || it.provisional || it.titleState=="conflict" ||
            (it.visual && it.state in setOf("conflict","inconclusive","pending"))
    }
    fun languagePending(evidence: List<ScanReviewStore.Entry>, target: String, confirmed: Boolean): Boolean =
        target.isBlank() || (!confirmed && evidence.any { !it.visual && !it.language.isNullOrBlank() && it.language!=target })

    fun identityPending(evidence: List<ScanReviewStore.Entry>, confirmed: Boolean, needsEdition: Boolean): Boolean =
        needsEdition || (!confirmed && hasIdentityConflict(evidence))
}
