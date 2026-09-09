package io.asv.mtgocr.ocrreader.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CardFrameColorDetectorTest {
    @Test fun recognizesCreamHeadersMeasuredFromSuppliedOldWhiteCards() {
        assertEquals("W", CardFrameColorDetector.classifyAverageRgb(193, 178, 164)) // Exile
        assertEquals("W", CardFrameColorDetector.classifyAverageRgb(170, 149, 122)) // Tariff
    }

    @Test fun recognizesBlueButLeavesNeutralAndOtherColorsUnclassified() {
        assertEquals("U", CardFrameColorDetector.classifyAverageRgb(88, 122, 165))
        assertEquals("", CardFrameColorDetector.classifyAverageRgb(145, 145, 145))
        assertEquals("", CardFrameColorDetector.classifyAverageRgb(170, 75, 55))
    }

    @Test fun readsColorFromRotatedNv21BeforeOcrMasking() {
        assertEquals("W", CardFrameColorDetector.detect(solidNv21(320, 240, 193, 178, 164), 320, 240, 1))
        assertEquals("U", CardFrameColorDetector.detect(solidNv21(320, 240, 88, 122, 165), 320, 240, 1))
    }

    private fun solidNv21(width: Int, height: Int, red: Int, green: Int, blue: Int): ByteArray {
        val y = (.299 * red + .587 * green + .114 * blue).toInt().coerceIn(0, 255)
        val u = ((blue - y) / 1.772 + 128).toInt().coerceIn(0, 255)
        val v = ((red - y) / 1.402 + 128).toInt().coerceIn(0, 255)
        return ByteArray(width * height * 3 / 2).also { frame ->
            frame.fill(y.toByte(), 0, width * height)
            for (index in width * height until frame.size step 2) {
                frame[index] = v.toByte()
                frame[index + 1] = u.toByte()
            }
        }
    }
}
