package io.asv.mtgocr.ocrreader.ui.camera

import io.asv.mtgocr.ocrreader.OcrTitleRegion
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLumaEnhancerTest {
    @Test fun increasesSeparationInWashedOutWhiteFrames() {
        val width = 32
        val height = 32
        val frame = ByteArray(width * height + width * height / 2) { 235.toByte() }
        val title = OcrTitleRegion.forFrame(width, height)
        val darkIndex = title.top * width + title.left
        val lightIndex = darkIndex + 1
        frame[darkIndex] = 190.toByte()
        val before = (235 - 190)
        OcrLumaEnhancer.enhance(frame, width, height, 0)
        val after = (frame[lightIndex].toInt() and 0xff) - (frame[darkIndex].toInt() and 0xff)
        assertTrue(after > before)
    }

    @Test fun leavesChromaPlaneUntouched() {
        val ySize = 16 * 16
        val frame = ByteArray(ySize + ySize / 2) { if (it < ySize) 180.toByte() else 77.toByte() }
        OcrLumaEnhancer.enhance(frame, 16, 16, 0)
        assertTrue(frame.copyOfRange(ySize, frame.size).all { it == 77.toByte() })
    }

    @Test fun masksEverythingExceptTheTitleBand() {
        val width = 80
        val height = 120
        val ySize = width * height
        val frame = ByteArray(ySize + ySize / 2) { 200.toByte() }
        val title = OcrTitleRegion.forFrame(width, height)
        OcrLumaEnhancer.enhance(frame, width, height, 0)
        assertTrue((frame[0].toInt() and 0xff) == 128)
        val center = ((title.top + title.bottom) / 2) * width + (title.left + title.right) / 2
        assertTrue((frame[center].toInt() and 0xff) != 128)
    }
}
