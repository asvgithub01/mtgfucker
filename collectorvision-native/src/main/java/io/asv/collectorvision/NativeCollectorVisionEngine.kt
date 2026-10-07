/* SPDX-License-Identifier: AGPL-3.0-or-later */
package io.asv.collectorvision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import ai.onnxruntime.*
import org.json.JSONArray
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

/** Blocking CPU pipeline. Own on a single worker; never call on the camera/UI thread. */
class NativeCollectorVisionEngine private constructor(private val directory: File, includeFra: Boolean) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val detector: OrtSession
    private val embedder: OrtSession
    private val catalog: ShortArray
    private val ids: List<String>
    private val halfLookup = FloatArray(65536) { CollectorVisionMath.half(it) }
    private var closed = false
    init {
        check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(min(4, Runtime.getRuntime().availableProcessors()))
        options.setInterOpNumThreads(1)
        // No NNAPI/GPU provider: numerical correctness before acceleration.
        try {
            detector = env.createSession(File(directory,"detector.onnx").absolutePath, options)
            try { embedder = env.createSession(File(directory,"milo.onnx").absolutePath, options) }
            catch (error: Throwable) { detector.close(); throw error }
        } finally { options.close() }
        try {
            val baseBytes = File(directory,"scryfall-mtg-embeddings.f16.bin").readBytes()
            val extraBytes = File(directory,CollectorVisionAssets.FRA_EMBEDDINGS).readBytes()
            val baseIds=JSONArray(File(directory,"scryfall-mtg-card-ids.json").readText())
            val extraIds=JSONArray(File(directory,CollectorVisionAssets.FRA_IDS).readText())
            require(baseIds.length()==109711 && baseBytes.size==baseIds.length()*128*2)
            require(extraIds.length()==CollectorVisionAssets.FRA_ROWS && extraBytes.size==extraIds.length()*128*2)
            val collectorBytes = File(directory,CollectorVisionAssets.COLLECTOR_EMBEDDINGS).readBytes()
            val collectorIds = JSONArray(File(directory,CollectorVisionAssets.COLLECTOR_IDS).readText())
            require(collectorIds.length() == CollectorVisionAssets.COLLECTOR_ROWS && collectorBytes.size == collectorIds.length()*128*2)
            val bytes=if(includeFra) baseBytes+extraBytes+collectorBytes else baseBytes
            val shorts=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            catalog=ShortArray(shorts.remaining()).also { shorts.get(it) }
            ids=(0 until baseIds.length()).map { baseIds.getString(it) } +
                if(includeFra) (0 until extraIds.length()).map { extraIds.getString(it) } +
                    (0 until collectorIds.length()).map { collectorIds.getString(it) } else emptyList()
            require(ids.size==ids.toSet().size) { "Duplicate visual catalog identifiers" }
        } catch (error: Throwable) { detector.close(); embedder.close(); throw error }
    }
    companion object {
        fun prepareCatalogComparison(context: Context, includeFra: Boolean): NativeCollectorVisionEngine =
            NativeCollectorVisionEngine(CollectorVisionAssets.prepare(context) {},includeFra)
        fun prepare(context: Context, onProgress: (String)->Unit = {}): NativeCollectorVisionEngine {
            val directory=CollectorVisionAssets.prepare(context,onProgress)
            onProgress("ONNX · CPU")
            return NativeCollectorVisionEngine(directory,true)
        }
    }
    private var scope: Set<String>? = null
    private var searchRows: IntArray? = null
    @Synchronized
    fun scan(bitmap: Bitmap, allowedIds: Set<String>? = null): ScanResult {
        check(!closed)
        if(scope !== allowedIds) {
            scope=allowedIds
            searchRows=VisualSearchScope.rows(ids,allowedIds)
        }
        val start=SystemClock.elapsedRealtime()
        val outputs=EdScanPerf.measure("engine.detect") { run(detector,bitmap,384,listOf("corners","sharpness")) }
        val corners=CollectorVisionMath.ordered(outputs[0],bitmap.width,bitmap.height)
        val sharpness=outputs[1].single()
        val present=sharpness.isFinite() && sharpness>=.02f && CollectorVisionMath.usable(corners)
        val detectionMs=SystemClock.elapsedRealtime()-start
        if (!present) return ScanResult(corners,false,sharpness,emptyList(),detectionMs,0)
        val recStart=SystemClock.elapsedRealtime()
        val crop=EdScanPerf.measure("engine.warp") { warp(bitmap,corners) }
        var photoRotated = false
        val hits=try {
            val normal=search(CollectorVisionMath.normalize(EdScanPerf.measure("engine.embed.normal") { run(embedder,crop,448,listOf("embedding")) }[0]))
            // Shortest-edge canonicalization can reverse the card. Test both orientations.
            val matrix=android.graphics.Matrix().apply { postRotate(180f) }
            val rotated=Bitmap.createBitmap(crop,0,0,crop.width,crop.height,matrix,true)
            try {
                val upsideDown=search(CollectorVisionMath.normalize(EdScanPerf.measure("engine.embed.rotated") { run(embedder,rotated,448,listOf("embedding")) }[0]))
                photoRotated = CollectorVisionMath.prefersRotated(normal, upsideDown)
                (normal+upsideDown).groupBy { it.cardId }.map { (_,values)->values.maxBy { it.score } }
                    .sortedByDescending { it.score }.take(8)
            } finally { if (rotated !== crop) rotated.recycle() }
        } finally { crop.recycle() }
        return ScanResult(CollectorVisionMath.photoCorners(corners, photoRotated),true,sharpness,hits,detectionMs,SystemClock.elapsedRealtime()-recStart, if (photoRotated) 180 else 0)
    }
    private fun run(session: OrtSession, bitmap: Bitmap, size: Int, names: List<String>): List<FloatArray> {
        val prepStarted=System.nanoTime()
        val model=if(size==384) "detector" else "embedder"
        val scaled=Bitmap.createScaledBitmap(bitmap,size,size,true)
        val pixels=IntArray(size*size)
        try { scaled.getPixels(pixels,0,size,0,0,size,size) }
        finally { if (scaled !== bitmap) scaled.recycle() }
        val data=FloatArray(3*size*size)
        val means=floatArrayOf(.485f,.456f,.406f); val std=floatArrayOf(.229f,.224f,.225f)
        for (i in pixels.indices) for (c in 0..2) {
            val byte=(pixels[i] ushr (16-c*8)) and 255
            data[c*pixels.size+i]=(byte/255f-means[c])/std[c]
        }
        EdScanPerf.record("model.$model.preprocess",System.nanoTime()-prepStarted)
        EdScanPerf.measure("model.$model.tensor") {
            OnnxTensor.createTensor(env,FloatBuffer.wrap(data),longArrayOf(1,3,size.toLong(),size.toLong()))
        }.use { tensor ->
            EdScanPerf.measure("model.$model.onnx") { session.run(mapOf("image" to tensor)) }.use { result ->
                return names.map { name ->
                    val output=result.get(name).orElseThrow { IllegalStateException("Missing ONNX output $name") } as OnnxTensor
                    val floats=output.floatBuffer
                    FloatArray(floats.remaining()).also { floats.get(it) }
                }
            }
        }
    }
    private fun warp(bitmap: Bitmap,corners: List<Corner>): Bitmap {
        val src=Mat(); val dst=Mat()
        val from=MatOfPoint2f(*corners.map { Point(it.x*bitmap.width.toDouble(),it.y*bitmap.height.toDouble()) }.toTypedArray())
        val to=MatOfPoint2f(Point(0.0,0.0),Point(447.0,0.0),Point(447.0,447.0),Point(0.0,447.0))
        val transform=Imgproc.getPerspectiveTransform(from,to)
        try {
            Utils.bitmapToMat(bitmap,src)
            Imgproc.warpPerspective(src,dst,transform,Size(448.0,448.0),Imgproc.INTER_LINEAR,Core.BORDER_REPLICATE)
            return Bitmap.createBitmap(448,448,Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(dst,it) }
        } finally { transform.release(); from.release(); to.release(); src.release(); dst.release() }
    }
    private fun search(query: FloatArray): List<Hit> = EdScanPerf.measure("engine.search") { searchUntimed(query) }
    private fun searchUntimed(query: FloatArray): List<Hit> {
        require(query.size==128)
        val bestScores=FloatArray(8) { Float.NEGATIVE_INFINITY }; val bestIds=IntArray(8) { -1 }
        val selected=searchRows
        for (index in 0 until (selected?.size ?: ids.size)) {
            val row=selected?.get(index) ?: index
            var sum=0f; val offset=row*128
            for (col in 0 until 128) sum+=halfLookup[catalog[offset+col].toInt() and 65535]*query[col]
            for (k in bestScores.indices) if (sum>bestScores[k]) {
                for (j in bestScores.lastIndex downTo k+1) { bestScores[j]=bestScores[j-1];bestIds[j]=bestIds[j-1] }
                bestScores[k]=sum;bestIds[k]=row;break
            }
        }
        return bestIds.indices.filter { bestIds[it]>=0 }.map { Hit(ids[bestIds[it]],bestScores[it]) }
    }
    @Synchronized override fun close() {
        if (!closed) { closed=true; detector.close(); embedder.close() }
        // OrtEnvironment is process-shared; do not close it here.
    }
}
