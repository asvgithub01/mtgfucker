package io.asv.mtgocr.ocrreader

/** Separate experiment: reuses the camera/crop flow but never runs OCR or edition lookup. */
class HashOnlyScanActivity : RapidEditionScanActivity() {
    override val hashOnlyMode: Boolean = true
}
