package io.asv.mtgocr.ocrreader.ui.camera

import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLumaEnhancerTest {
    @Test fun increasesSeparationInWashedOutWhiteFrames() {
        val frame = ByteArray(32 * 32 + 32 * 16) { 235.toByte() }
        for (index in 0 until 32 * 32 step 9) frame[index] = 190.toByte()
        val before = (235 - 190)
        OcrLumaEnhancer.enhance(frame, 32, 32)
        val after = (frame[1].toInt() and 0xff) - (frame[0].toInt() and 0xff)
        assertTrue(after > before)
    }

    @Test fun leavesChromaPlaneUntouched() {
        val ySize = 16 * 16
        val frame = ByteArray(ySize + ySize / 2) { if (it < ySize) 180.toByte() else 77.toByte() }
        OcrLumaEnhancer.enhance(frame, 16, 16)
        assertTrue(frame.copyOfRange(ySize, frame.size).all { it == 77.toByte() })
    }
}
