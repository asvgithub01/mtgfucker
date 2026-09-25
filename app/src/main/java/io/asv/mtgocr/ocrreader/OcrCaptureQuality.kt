package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Color

/** Diagnostic only: texture/sharpness cannot establish whether a finger covers the card. */
internal object OcrCaptureQuality {
    fun measure(card: Bitmap): Map<String, Double> {
        val scaled = Bitmap.createScaledBitmap(card, 315, 440, true)
        return try {
            val pixels = IntArray(315 * 440)
            scaled.getPixels(pixels, 0, 315, 0, 0, 315, 440)
            val luma = pixels.map { (Color.red(it) * 77 + Color.green(it) * 150 + Color.blue(it) * 29) / 256.0 }
            fun variance(top: Int, bottom: Int): Double {
                var sum = 0.0; var squares = 0.0; var count = 0
                for (y in top until bottom) for (x in 10 until 305) {
                    val i = y * 315 + x
                    val lap = 4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - 315] - luma[i + 315]
                    sum += lap; squares += lap * lap; count++
                }
                return (squares / count - (sum / count) * (sum / count)).coerceAtLeast(0.0)
            }
            mapOf("width" to card.width.toDouble(), "height" to card.height.toDouble(),
                "titleLaplacianVariance" to variance(10, 64), "footerLaplacianVariance" to variance(396, 434))
        } finally { if (scaled !== card) scaled.recycle() }
    }
}
