package io.asv.mtgocr.ocrreader

/** Pure OpenGL column-major projection, shared by renderer and unit tests. */
object ArCardProjection {
    fun project(matrix: FloatArray, point: FloatArray, width: Int, height: Int): FloatArray? {
        if(matrix.size!=16 || point.size!=3 || width<=0 || height<=0) return null
        fun component(row: Int)=matrix[row]*point[0]+matrix[4+row]*point[1]+matrix[8+row]*point[2]+matrix[12+row]
        val w=component(3)
        if(!w.isFinite() || w<=0.0001f) return null
        val x=component(0)/w; val y=component(1)/w; val z=component(2)/w
        if(!x.isFinite() || !y.isFinite() || !z.isFinite() || z < -1f || z > 1f) return null
        return floatArrayOf((x+1)*width/2,(1-y)*height/2)
    }
}
