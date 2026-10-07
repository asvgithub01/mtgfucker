package io.asv.mtgocr.ocrreader

/** Snapshot geometry is discarded quickly; this is a 2D detector, not a 3D AR anchor. */
data class ArGenericOutline(val points: FloatArray,val capturedAt: Long,val width: Int,val height: Int,val rotation: Int) {
    fun valid(now: Long,w: Int,h: Int,r: Int)=now-capturedAt in 0..250 && width==w && height==h && rotation==r
    companion object {
        fun project(x: Float,y: Float,basis: FloatArray): FloatArray =
            floatArrayOf(basis[0]+x*(basis[2]-basis[0])+y*(basis[4]-basis[0]),
                basis[1]+x*(basis[3]-basis[1])+y*(basis[5]-basis[1]))
    }
}
