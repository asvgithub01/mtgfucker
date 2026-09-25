package io.asv.mtgocr.ocrreader

/** Camera framing is independent of flags; V2 may retain more pixels during rectification. */
internal object SymbolResolutionPolicy {
    const val CAMERA_WIDTH = 1280
    const val CAMERA_HEIGHT = 960
    fun cardWidth(highResolution: Boolean) = if (highResolution) 1260 else 630
    fun cardHeight(highResolution: Boolean) = if (highResolution) 1760 else 880
    fun segmentationWidth(cardWidth: Int) = if (cardWidth >= 1000) 1488 else 744
}
