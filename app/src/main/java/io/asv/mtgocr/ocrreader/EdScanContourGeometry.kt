package io.asv.mtgocr.ocrreader

/** Coordinates only: never detects, recognizes, or authorizes a card. */
internal object EdScanContourGeometry {
    const val MAX_AGE_MS=2500L
    fun fresh(capture:Long,now:Long)=now>=capture && now-capture<MAX_AGE_MS
    fun valid(points:FloatArray)=points.size==8 && points.all { it.isFinite() }
    fun project(upright:FloatArray,rotation:Int,imageQuad:FloatArray):FloatArray? {
        if(!valid(upright) || !valid(imageQuad) || rotation !in listOf(0,90,180,270)) return null
        return FloatArray(8).also { out ->
            for(i in 0..3) {
                val x=upright[i*2];val y=upright[i*2+1]
                val (u,v)=when(rotation) { 90 -> y to 1-x;180 -> 1-x to 1-y;270 -> 1-y to x;else -> x to y }
                for(c in 0..1) out[2*i+c]=(1-u)*(1-v)*imageQuad[c]+u*(1-v)*imageQuad[2+c]+u*v*imageQuad[4+c]+(1-u)*v*imageQuad[6+c]
            }
        }
    }
}
