package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.*
import android.util.AtomicFile
import io.asv.collectorvision.EdScanJobs
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Raw pixels from the SAME frame as the OCR result, before luminance enhancement. */
object OcrFramePhoto {
    class Frame(val id: Int, val bytes: ByteArray, val width: Int, val height: Int, val rotation: Int)
    @JvmStatic fun snapshot(id: Int, bytes: ByteArray, width: Int, height: Int, rotation: Int) =
        Frame(id, bytes.clone(), width, height, rotation)
    private val writer = Executors.newSingleThreadExecutor()
    @JvmStatic fun save(context: Context, frame: Frame?, name: String): String? {
        if (frame == null || !ScannerSettings.saveOcrPhotos(context)) return null
        val key = UUID.randomUUID().toString()
        val app = context.applicationContext
        writer.execute {
            runCatching {
                val bytes = ByteArrayOutputStream().use { out ->
                    check(YuvImage(frame.bytes, ImageFormat.NV21, frame.width, frame.height, null)
                        .compressToJpeg(Rect(0,0,frame.width,frame.height), 95, out)); out.toByteArray()
                }
                val raw = BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("Invalid NV21")
                val rotated = Bitmap.createBitmap(raw,0,0,raw.width,raw.height,
                    Matrix().apply { postRotate(frame.rotation * 90f) },true)
                try {
                    val jpeg = ByteArrayOutputStream().use { out ->
                        check(rotated.compress(Bitmap.CompressFormat.JPEG,95,out)); out.toByteArray()
                    }
                    write(File(EdScanJobs.directory(app), "$key.jpg"), jpeg)
                    write(File(EdScanJobs.directory(app), "$key.json"), JSONObject()
                        .put("source","ocr").put("frameId", frame.id).put("rotation",frame.rotation)
                        .put("expectedName",name).put("createdAt",System.currentTimeMillis()).toString().toByteArray())
                } finally { if (rotated !== raw) rotated.recycle(); raw.recycle() }
            }.onFailure { android.util.Log.e("OcrFramePhoto", "Cannot save accepted frame", it) }
        }
        return key
    }
    private fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file); val out = atomic.startWrite()
        try { out.write(bytes); atomic.finishWrite(out) } catch (e: Exception) { atomic.failWrite(out); throw e }
    }
}
