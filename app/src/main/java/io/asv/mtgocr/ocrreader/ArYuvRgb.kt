package io.asv.mtgocr.ocrreader

import java.nio.ByteBuffer

/** Handles planar/interleaved YUV_420_888, including row padding and buffer offsets. */
object ArYuvRgb {
    data class Plane(val buffer: ByteBuffer,val rowStride: Int,val pixelStride: Int)
    fun pixels(width: Int,height: Int,left: Int,top: Int,y: Plane,u: Plane,v: Plane): IntArray {
        require(width>0 && height>0 && left>=0 && top>=0)
        // Bulk reads avoid millions of direct-buffer accesses per camera frame.
        // Duplicate preserves each Image plane's position (including interleaved UV offsets).
        fun copy(p: Plane)=p.buffer.duplicate().let { b -> ByteArray(b.remaining()).also { b.get(it) } }
        val ys=copy(y); val us=copy(u); val vs=copy(v)
        val result=IntArray(width*height)
        for(row in 0 until height) for(col in 0 until width) {
            val x=left+col; val yy=top+row
            val l=((ys[yy*y.rowStride+x*y.pixelStride].toInt() and 255)-16).coerceAtLeast(0)
            val cb=(us[(yy/2)*u.rowStride+(x/2)*u.pixelStride].toInt() and 255)-128; val cr=(vs[(yy/2)*v.rowStride+(x/2)*v.pixelStride].toInt() and 255)-128
            val r=((298*l+409*cr+128) shr 8).coerceIn(0,255)
            val g=((298*l-100*cb-208*cr+128) shr 8).coerceIn(0,255)
            val b=((298*l+516*cb+128) shr 8).coerceIn(0,255)
            result[row*width+col]=(255 shl 24) or (r shl 16) or (g shl 8) or b
        }
        return result
    }
}
