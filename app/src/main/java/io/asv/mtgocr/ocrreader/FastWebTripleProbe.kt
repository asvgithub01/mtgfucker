package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Matrix
import io.asv.collectorvision.FastWebDiegoEngine
import io.asv.collectorvision.ScanResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opt-in debug-only camera evidence. No collection, preferences, networking or acceptance. */
internal object FastWebTripleProbe {
    fun enabled(cache:File)=BuildConfig.DEBUG && File(cache,"fastweb-triple-probe/enabled").isFile

    fun capture(cache:File,raw:Bitmap,rotation:Int,engine:FastWebDiegoEngine,onDetected:(ScanResult)->Unit) {
        val root=File(cache,"fastweb-triple-probe")
        val request=File(root,"request")
        if(!request.isFile || !request.delete()) return
        val dir=File(root,"sample-${System.currentTimeMillis()}");check(dir.mkdir())
        fun png(name:String,b:Bitmap)=File(dir,name).outputStream().use { check(b.compress(Bitmap.CompressFormat.PNG,100,it)) }
        val upright=if(rotation==0) raw else Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(rotation.toFloat()) },true)
        try {
            png("raw.png",raw);png("upright.png",upright)
            val result=engine.scan(upright,onDetected=onDetected,onInput={ crop,rgb,tensor ->
                png("diego-crop.png",crop)
                File(dir,"input.rgb").writeBytes(rgb)
                val bytes=ByteBuffer.allocate(tensor.size*4).order(ByteOrder.LITTLE_ENDIAN)
                bytes.asFloatBuffer().put(tensor);File(dir,"input.f32").writeBytes(bytes.array())
            })
            val q=JSONArray();result.corners.forEach { q.put(JSONArray(listOf(it.x,it.y))) }
            val hits=JSONArray();result.hits.forEach { hits.put(JSONObject().put("id",it.cardId).put("score",it.score)) }
            File(dir,"result.json").writeText(JSONObject().put("rotation",rotation).put("quad",q).put("hits",hits)
                .put("present",result.present).put("note","Diagnostic IO included: do not use for latency benchmarks").toString(2))
        } finally { if(upright!==raw) upright.recycle() }
    }
}
