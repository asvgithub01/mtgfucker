package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.CvType
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Experimental OCR-only processing. Never feed the result into artwork hashes. */
object OcrImageEnhancement {
    private val ready by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

    @JvmStatic fun claheBytes(input: ByteArray, width: Int, height: Int): ByteArray? {
        if (!ready || width <= 0 || height <= 0 || input.size != width * height) return null
        val src = Mat(height, width, CvType.CV_8UC1)
        val dst = Mat()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        return try {
            src.put(0, 0, input)
            clahe.apply(src, dst)
            ByteArray(input.size).also { dst.get(0, 0, it) }
        } finally { src.release(); dst.release(); clahe.collectGarbage() }
    }

    fun clahe(source: Bitmap): Bitmap {
        if (!ready) return source.copy(Bitmap.Config.ARGB_8888, false)
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val bytes = ByteArray(source.width * source.height)
            gray.get(0, 0, bytes)
            val enhanced = claheBytes(bytes, source.width, source.height) ?: bytes
            gray.put(0, 0, enhanced)
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(gray, it) }
        } finally { rgba.release(); gray.release() }
    }
}
