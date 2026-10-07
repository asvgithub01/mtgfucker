package io.asv.mtgocr.ocrreader

/** Finish/price changes do not require rebuilding the image reference. */
object ArReferencePolicy {
    fun canReuse(oldPrinting: String?,oldImage: String?,printing: String?,image: String?,requested: Boolean): Boolean =
        requested && !printing.isNullOrBlank() && !image.isNullOrBlank() &&
            oldPrinting==printing && oldImage==image
}
