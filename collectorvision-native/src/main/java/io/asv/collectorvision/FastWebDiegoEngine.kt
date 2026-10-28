package io.asv.collectorvision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import ai.onnxruntime.*
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.sqrt

/** Experimental upright-only pipeline. Worker-confined; no Cornelius or Milo fallback. */
class FastWebDiegoEngine private constructor(dir: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val sessions = mutableListOf<OrtSession>()
    private val ids: List<String>
    private val detector: OrtSession
    private val encoder: OrtSession
    private val search: OrtSession
    init {
        check(OpenCVLoader.initLocal())
        val array = JSONArray(File(dir, "ids.json").readText())
        ids = (0 until array.length()).map { array.getString(it) }
        require(ids.size == 123376 && ids.toSet().size == ids.size)
        try {
            detector = open(File(dir,"detector.onnx"), true)
            encoder = open(File(dir,"diego.onnx"), false)
            search = open(File(dir,"search.onnx"), false)
        } catch (e: Throwable) { close(); throw e }
    }
    private fun open(file: File, basic: Boolean): OrtSession = OrtSession.SessionOptions().use {
        it.setIntraOpNumThreads(4); it.setInterOpNumThreads(1)
        // Android ORT 1.20 extended fusion creates duplicate nodes in FastWeb 1.39.
        if (basic) it.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        env.createSession(file.absolutePath,it).also { session -> sessions.add(session) }
    }
    private var scope: Set<String>? = null
    private var bias = FloatArray(ids.size)
    private var rows = ids.size
    @Synchronized fun scan(bitmap: Bitmap, allowedIds: Set<String>? = null, onDetected:((ScanResult)->Unit)?=null,
        onInput:((Bitmap,ByteArray,FloatArray)->Unit)?=null): ScanResult {
        check(sessions.isNotEmpty())
        if (allowedIds != scope) {
            scope = allowedIds?.toSet()
            bias = FloatArray(ids.size) { if (allowedIds == null || ids[it] in allowedIds) 0f else -1e6f }
            rows = bias.count { it == 0f }
        }
        val detected = detect(bitmap)
        val corners = detected.corners
        onDetected?.invoke(detected) // Geometry feedback before the encoder; never authorizes a save.
        if (!detected.present || rows == 0) return detected
        val rec = SystemClock.elapsedRealtime()
        val crop = warp(bitmap, corners)
        val embedding = try {
            val p = IntArray(crop.width*crop.height)
            crop.getPixels(p,0,crop.width,0,0,crop.width,crop.height)
            val rgb = FastWebPreprocess.diegoView(p,crop.width,crop.height,false,true)
            val data = FastWebPreprocess.diegoTensor(rgb)
            // Synchronous diagnostic observer: bitmap belongs to this call, never retain/recycle it.
            onInput?.invoke(crop,rgb,data)
            OnnxTensor.createTensor(env,FloatBuffer.wrap(data),longArrayOf(1,3,448,320)).use { x ->
                encoder.run(mapOf(encoder.inputNames.first() to x)).use { floats(it[0]) }
            }.also { v ->
                require(v.size == 256 && v.all { it.isFinite() })
                val norm = sqrt(v.sumOf { it.toDouble()*it })
                require(norm > 0); for (i in v.indices) v[i] = (v[i]/norm).toFloat()
            }
        } finally { crop.recycle() }
        val searchStart = SystemClock.elapsedRealtime()
        // One upright encoder pass; both slots in the frozen search graph receive that vector.
        val hits = OnnxTensor.createTensor(env,FloatBuffer.wrap(embedding+embedding),longArrayOf(2,256)).use { query ->
            OnnxTensor.createTensor(env,FloatBuffer.wrap(bias),longArrayOf(ids.size.toLong())).use { mask ->
                search.run(mapOf("query" to query,"scope_bias" to mask)).use { r ->
                    val scores = floats(r[0]); val indices = (r[1] as OnnxTensor).longBuffer
                    scores.map { score -> Hit(ids[indices.get().toInt()],score) }.filter { it.score.isFinite() && it.score > -1f }
                }
            }
        }
        return detected.copy(hits=hits, recognitionMs=SystemClock.elapsedRealtime()-rec,
            searchMs=SystemClock.elapsedRealtime()-searchStart,searchedRows=rows)
    }
    /** Detector-only path for Triple's independent fresh OCR lane. Does not run Diego. */
    @Synchronized fun detect(bitmap:Bitmap):ScanResult {
        check(sessions.isNotEmpty())
        val start = SystemClock.elapsedRealtime()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val input = FastWebPreprocess.tensor(FastWebPreprocess.corneliusExact(pixels,bitmap.width,bitmap.height,384))
        val q: FloatArray
        val sharpness: Float
        OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,3,384,384)).use { x ->
            detector.run(mapOf("image" to x)).use { r ->
                q = floats(r.get("corners").get()); sharpness = floats(r.get("sharpness").get())[0]
            }
        }
        val ordered = try {
            require(sharpness.isFinite() && sharpness >= .02f && sharpness <= 1f)
            FastWebQuad.order(q,bitmap.width,bitmap.height)
        } catch (_: IllegalArgumentException) { null }
        val corners = ordered?.map { Corner(it[0].toFloat(),it[1].toFloat()) } ?: emptyList()
        return ScanResult(corners,ordered != null,sharpness,emptyList(),SystemClock.elapsedRealtime()-start,0)
    }
    override fun close() { sessions.reversed().forEach { runCatching { it.close() } }; sessions.clear() }
    companion object {
        const val ID = "FastWeb1.39+DiegoFP32"
        private fun floats(value: OnnxValue): FloatArray = (value as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
        /** Same 640x896 perspective interpolation as the validated Sony lab. */
        internal fun warp(bitmap: Bitmap, corners: List<Corner>): Bitmap {
            val src=Mat(); val dst=Mat(); var transform:Mat?=null
            val from=MatOfPoint2f(*corners.map { Point(it.x.toDouble()*bitmap.width,it.y.toDouble()*bitmap.height) }.toTypedArray())
            val to=MatOfPoint2f(Point(0.0,0.0),Point(639.0,0.0),Point(639.0,895.0),Point(0.0,895.0))
            try {
                Utils.bitmapToMat(bitmap,src);transform=Imgproc.getPerspectiveTransform(from,to)
                Imgproc.warpPerspective(src,dst,transform,Size(640.0,896.0))
                return Bitmap.createBitmap(640,896,Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(dst,it) }
            } finally { src.release();dst.release();from.release();to.release();transform?.release() }
        }
        @Synchronized fun prepare(context: Context, onProgress:(String)->Unit = {}):FastWebDiegoEngine {
            val manifest=JSONObject(context.assets.open("fastweb/manifest.json").bufferedReader().use { it.readText() })
            val dir=File(context.filesDir,"fastweb-models-v1").apply { check(isDirectory || mkdirs()) }
            for (name in listOf("detector.onnx","diego.onnx","search.onnx","ids.json")) {
                onProgress("FastWeb + Diego · $name")
                val expected=manifest.getString(name);val target=File(dir,name)
                fun sha(file:File):String {
                    val md=MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { stream -> val b=ByteArray(65536);while(true){val n=stream.read(b);if(n<0)break;md.update(b,0,n)} }
                    return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                }
                if (!target.exists() || sha(target)!=expected) {
                    val temp=File(dir,"$name.tmp")
                    try {
                        context.assets.open("fastweb/$name").use { input -> temp.outputStream().use { input.copyTo(it) } }
                        check(sha(temp)==expected) { "FastWeb asset checksum: $name" }
                        check(temp.renameTo(target)) { "FastWeb asset install: $name" }
                    } finally { temp.delete() }
                }
            }
            return FastWebDiegoEngine(dir)
        }
    }
}
