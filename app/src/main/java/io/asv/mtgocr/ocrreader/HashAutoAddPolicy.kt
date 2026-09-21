package io.asv.mtgocr.ocrreader

internal data class HashAutoAddTarget(
    val cardName: String,
    val printingUuid: String
)

/** Never writes a scan automatically when the evidence still points to several printings. */
internal object HashAutoAddPolicy {
    fun uniqueTarget(targets: List<HashAutoAddTarget>): HashAutoAddTarget? =
        targets.distinctBy(HashAutoAddTarget::printingUuid).singleOrNull()
}
