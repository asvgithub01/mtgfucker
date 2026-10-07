package io.asv.mtgocr.ocrreader

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class ArYuvRgbTest {
    private fun plane(bytes: IntArray,row: Int,pixel: Int=1,offset: Int=0)=ArYuvRgb.Plane(
        ByteBuffer.wrap(bytes.map { it.toByte() }.toByteArray()).apply { position(offset) },row,pixel)
    @Test fun paddedRowsAndPlanarChroma() {
        val y=plane(intArrayOf(16,235,0,0,235,16,0,0),4)
        val c=plane(intArrayOf(128),1)
        assertArrayEquals(intArrayOf(0xff000000.toInt(),0xffffffff.toInt(),0xffffffff.toInt(),0xff000000.toInt()),ArYuvRgb.pixels(2,2,0,0,y,c,c))
    }
    @Test fun interleavedUvWithIndependentBufferPositions() {
        val y=plane(IntArray(4){81},2)
        val uv=ByteBuffer.wrap(byteArrayOf(90.toByte(),240.toByte()))
        val u=ArYuvRgb.Plane(uv.duplicate(),2,2)
        val v=ArYuvRgb.Plane(uv.duplicate().apply { position(1) },2,2)
        for(color in ArYuvRgb.pixels(2,2,0,0,y,u,v)) {
            assertTrue((color ushr 16 and 255)>=250)
            assertTrue((color ushr 8 and 255)<=3)
            assertTrue((color and 255)<=3)
        }
    }
    @Test fun respectsCropAndPlaneOffset() {
        val y=IntArray(1+24){16}.apply { this[1+2*6+2]=235; this[1+2*6+3]=235 }
        val c=plane(IntArray(4){128},2)
        val actual=ArYuvRgb.pixels(2,2,2,2,plane(y,6,offset=1),c,c)
        assertArrayEquals(intArrayOf(-1,-1,0xff000000.toInt(),0xff000000.toInt()),actual)
    }
    @Test fun directBuffersOddCropMatchReferenceWithoutChangingPositions() {
        val random=java.util.Random(73)
        fun direct(size: Int,row: Int,pixel: Int): ArYuvRgb.Plane {
            val bytes=ByteArray(size).also { random.nextBytes(it) }
            return ArYuvRgb.Plane(ByteBuffer.allocateDirect(size).apply { put(bytes); position(1) },row,pixel)
        }
        val y=direct(1+8*8,8,1); val u=direct(1+8*4,8,2); val v=direct(1+8*4,8,2)
        val result=ArYuvRgb.pixels(5,5,1,1,y,u,v)
        fun sample(p: ArYuvRgb.Plane,x: Int,row: Int)=p.buffer.get(1+row*p.rowStride+x*p.pixelStride).toInt() and 255
        for(row in 0 until 5) for(col in 0 until 5) {
            val x=col+1; val yy=row+1
            val l=(sample(y,x,yy)-16).coerceAtLeast(0)
            val cb=sample(u,x/2,yy/2)-128; val cr=sample(v,x/2,yy/2)-128
            val r=((298*l+409*cr+128) shr 8).coerceIn(0,255)
            val g=((298*l-100*cb-208*cr+128) shr 8).coerceIn(0,255)
            val b=((298*l+516*cb+128) shr 8).coerceIn(0,255)
            assertEquals((255 shl 24) or (r shl 16) or (g shl 8) or b,result[row*5+col])
        }
        listOf(y,u,v).forEach { assertEquals(1,it.buffer.position()) }
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsEmptyDimensions() {
        val p=plane(intArrayOf(128),1); ArYuvRgb.pixels(0,2,0,0,p,p,p)
    }
}
