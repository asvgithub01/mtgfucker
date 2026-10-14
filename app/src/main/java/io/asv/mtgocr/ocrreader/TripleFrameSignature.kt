package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap

/** Small normalized appearance fingerprint; not a card recognizer or an edition classifier. */
internal object TripleFrameSignature {
    fun read(bitmap: Bitmap?): FloatArray? {
        if(bitmap==null) return null
        val thumb=Bitmap.createScaledBitmap(bitmap,16,22,true)
        val pixels=IntArray(16*22)
        try { thumb.getPixels(pixels,0,16,0,0,16,22) } finally { if(thumb!==bitmap) thumb.recycle() }
        // Inner artwork avoids borders, text language and AR quad edges.
        val values=(3..12).flatMap { y -> (2..13).map { x ->
            val p=pixels[y*16+x]; (((p shr 16) and 255)*.299f+((p shr 8) and 255)*.587f+(p and 255)*.114f)/255f
        } }.toFloatArray()
        val mean=values.average().toFloat()
        val contrast=kotlin.math.sqrt(values.sumOf { ((it-mean)*(it-mean)).toDouble() }/values.size).toFloat()
        if(contrast<.035f) return null // Blank/occluded frames must not authorize another copy.
        return values.map { ((it-mean)/(contrast*4)).coerceIn(-1f,1f) }.toFloatArray()
    }
}
