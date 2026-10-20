/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Corner ordering and validity gates adapted from CollectorVision scanner.worker.mjs.
 */
package io.asv.collectorvision

import kotlin.math.*

data class Corner(val x: Float, val y: Float)
data class Hit(val cardId: String, val score: Float)
data class ScanResult(val corners: List<Corner>, val present: Boolean, val sharpness: Float,
    val hits: List<Hit>, val detectionMs: Long, val recognitionMs: Long, val photoRotation: Int = 0,
    val searchMs: Long = 0, val searchedRows: Int = 0,
    val regionConflict:Boolean=false,val regionDiagnostics:String="",val comparisonRegion:String="full")

internal object CollectorVisionMath {
    fun photoCorners(corners: List<Corner>, rotated: Boolean): List<Corner> =
        if (rotated && corners.size == 4) corners.drop(2) + corners.take(2) else corners
    fun prefersRotated(normal: List<Hit>, rotated: List<Hit>): Boolean =
        (rotated.firstOrNull()?.score ?: Float.NEGATIVE_INFINITY) >
            (normal.firstOrNull()?.score ?: Float.NEGATIVE_INFINITY)
    fun half(bits: Int): Float {
        val sign = if (bits and 0x8000 == 0) 1f else -1f
        val exponent = (bits ushr 10) and 31
        val mantissa = bits and 1023
        return when (exponent) {
            0 -> sign * Math.scalb(mantissa.toFloat(), -24)
            31 -> if (mantissa == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
            else -> sign * Math.scalb(1f + mantissa / 1024f, exponent - 15)
        }
    }
    fun normalize(values: FloatArray): FloatArray {
        var sum = 0.0
        for (v in values) { require(v.isFinite()); sum += v.toDouble() * v }
        val norm = sqrt(sum)
        require(norm > 1e-12) { "Zero embedding" }
        for (i in values.indices) values[i] = (values[i] / norm).toFloat()
        return values
    }
    fun ordered(raw: FloatArray, width: Int, height: Int): List<Corner> {
        require(raw.size == 8)
        if (raw.any { !it.isFinite() }) return emptyList()
        val pts = (0..3).map { Corner(raw[it*2].coerceIn(0f,1f),raw[it*2+1].coerceIn(0f,1f)) }
        val cx = pts.sumOf { it.x.toDouble() } / 4
        val cy = pts.sumOf { it.y.toDouble() } / 4
        val ordered = pts.sortedBy { atan2(it.y-cy,it.x-cx) }
        val shortest = ordered.indices.minBy { i ->
            val a=ordered[i]; val b=ordered[(i+1)%4]
            hypot((a.x-b.x)*width,(a.y-b.y)*height)
        }
        return (0..3).map { ordered[(it+shortest)%4] }
    }
    /** Portrait input with the printed title at the top. Preserve winding; never mirror. */
    fun uprightCorners(corners:List<Corner>):List<Corner> {
        if(corners.size!=4 || corners.any { !it.x.isFinite() || !it.y.isFinite() }) return corners
        return if(corners[0].y+corners[1].y <= corners[2].y+corners[3].y) corners
            else corners.drop(2)+corners.take(2)
    }
    fun usable(points: List<Corner>): Boolean {
        if (points.size != 4 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return false
        var twiceArea=0f
        var positive=false; var negative=false
        for (i in 0..3) {
            val a=points[i]; val b=points[(i+1)%4]; val c=points[(i+2)%4]
            twiceArea += a.x*b.y-b.x*a.y
            val cross=(b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x)
            if (abs(cross)<1e-6f) return false
            positive = positive || cross>0; negative=negative || cross<0
            for (j in i+1..3) {
                val other=points[j]
                if ((a.x-other.x).pow(2)+(a.y-other.y).pow(2)<0.0004f) return false
            }
        }
        return abs(twiceArea)*.5f>=.01f && !(positive && negative)
    }
}
