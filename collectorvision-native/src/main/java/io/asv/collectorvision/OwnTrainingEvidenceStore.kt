package io.asv.collectorvision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in research evidence only. No collection, OCR jobs, cloud sync or automatic training.
 * One in-flight sample; disk work is off camera thread. Caller retains ownership of inputs.
 * Not wired to a scanner until its explicit collection-mode UI is implemented.
 */
class OwnTrainingEvidenceStore(context: Context, private val quotaBytes: Long = 512L*1024*1024) : AutoCloseable {
    private val root = File(context.applicationContext.filesDir, "own-training-evidence-v1")
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    val sessionId: String = UUID.randomUUID().toString()
    @Volatile var enabled = false
    @Volatile var lastOutcome = "disabled"
        private set
    private var lastAcceptedMs = -2000L

    /** Metadata must describe this exact frame, not the latest asynchronous result.
     * Include model IDs/hashes, frame timestamp, normalized corners, top-k and timings.
     * A missing crop is allowed to preserve failed detections and negative examples.
     */
    @Synchronized fun offer(frame: Bitmap, crop: Bitmap?, metadata: JSONObject): Boolean {
        if (!enabled || closed.get()) return false
        val now = SystemClock.elapsedRealtime()
        if (now-lastAcceptedMs<1500 || !busy.compareAndSet(false,true)) return false
        var frameCopy: Bitmap? = null
        var cropCopy: Bitmap? = null
        try {
            frameCopy = requireNotNull(frame.copy(Bitmap.Config.ARGB_8888,false))
            cropCopy = crop?.let { requireNotNull(it.copy(Bitmap.Config.ARGB_8888,false)) }
            val f = frameCopy; val c = cropCopy
            val record = JSONObject(metadata.toString()).put("schema",1).put("sessionId",sessionId)
                .put("captureId",UUID.randomUUID().toString()).put("createdAt",System.currentTimeMillis())
                .put("labelStatus","unreviewed").put("trainingEligible",false)
                .put("frameWidth",f.width).put("frameHeight",f.height)
            lastOutcome="queued"
            worker.execute {
                try {
                    check(root.isDirectory || root.mkdirs())
                    // Count all files, including partial writes; never evict user evidence silently.
                    val used = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    if (used>=quotaBytes || root.usableSpace<256L*1024*1024) {
                        lastOutcome="quota_or_disk_limit"; return@execute
                    }
                    fun png(b:Bitmap):ByteArray = ByteArrayOutputStream().use {
                        check(b.compress(Bitmap.CompressFormat.PNG,100,it));it.toByteArray()
                    }
                    val files = linkedMapOf("frame.png" to png(f))
                    if(c!=null) files["crop.png"]=png(c)
                    record.put("frameFile","frame.png").put("cropFile",if(c!=null) "crop.png" else JSONObject.NULL)
                    files["metadata.json"]=record.toString(2).toByteArray(Charsets.UTF_8)
                    val required=files.values.sumOf { it.size.toLong() }
                    if(used+required>quotaBytes || root.usableSpace<required+256L*1024*1024) {
                        lastOutcome="quota_or_disk_limit";return@execute
                    }
                    val dir=File(root,sessionId+"/"+record.getString("captureId"))
                    check(dir.mkdirs())
                    for((name,bytes) in files) File(dir,name).outputStream().use { it.write(bytes) }
                    // Only samples with this marker are complete/exportable. Partial samples stay quarantined.
                    File(dir,"complete").writeText("1")
                    lastOutcome="saved"
                } catch(e:Exception) {
                    lastOutcome="error:"+e.javaClass.simpleName
                    android.util.Log.e("OwnTrainingEvidence","Evidence write failed",e)
                } finally { f.recycle();c?.recycle();busy.set(false) }
            }
            lastAcceptedMs=now;return true
        } catch(e:Exception) {
            frameCopy?.recycle();cropCopy?.recycle();busy.set(false)
            lastOutcome="error:"+e.javaClass.simpleName;return false
        }
    }
    /** Human correction is separate from immutable predictions; it never updates a collection. */
    fun review(session: String, capture: String, oracleId: String?, illustrationId: String?,
               note: String): java.util.concurrent.Future<Boolean> = worker.submit<Boolean> {
        fun uuid(value:String):String = UUID.fromString(value).toString().also { require(it==value) }
        val dir=File(root,uuid(session)+"/"+uuid(capture))
        require(File(dir,"complete").isFile)
        require(note.length<=2000)
        oracleId?.let { uuid(it) };illustrationId?.let { uuid(it) }
        val review=JSONObject().put("schema",1).put("reviewedAt",System.currentTimeMillis())
            .put("source","human").put("oracleId",oracleId ?: JSONObject.NULL)
            .put("illustrationId",illustrationId ?: JSONObject.NULL).put("note",note)
            .put("trainingEligible",false) // Dataset splitting remains an offline, reviewed decision.
        val bytes=review.toString(2).toByteArray(Charsets.UTF_8)
        val used=root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        check(used+bytes.size<=quotaBytes && root.usableSpace>256L*1024*1024)
        val temp=File(dir,"review.tmp");temp.writeBytes(bytes)
        check(temp.renameTo(File(dir,"review.json")))
        true
    }

    /** Caller supplies a user-selected output stream (SAF); worker owns/closes it.
     * Only completed samples and known evidence filenames are exported. Never scans app data.
     */
    fun export(output: java.io.OutputStream): java.util.concurrent.Future<Int> {
        try { return worker.submit<Int> {
            java.util.zip.ZipOutputStream(output).use { zip ->
                var count=0
                root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { session ->
                    session.listFiles()?.filter { it.isDirectory && File(it,"complete").isFile }
                        ?.sortedBy { it.name }?.forEach { sample ->
                        for(name in listOf("frame.png","crop.png","metadata.json","review.json","complete")) {
                            val file=File(sample,name)
                            if(file.isFile) {
                                zip.putNextEntry(java.util.zip.ZipEntry(session.name+"/"+sample.name+"/"+name))
                                file.inputStream().use { it.copyTo(zip) };zip.closeEntry()
                            }
                        }
                        count++
                    }
                }
                count
            }
        } } catch(e:Exception) { output.close();throw e }
    }
    override fun close() { enabled=false;closed.set(true);worker.shutdown() }
}
