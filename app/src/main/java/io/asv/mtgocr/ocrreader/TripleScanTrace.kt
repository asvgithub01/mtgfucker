package io.asv.mtgocr.ocrreader

import android.content.Context
import io.asv.collectorvision.EdScanPerf
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Bounded diagnostic trace survives unplugged tests. No photos, OCR text or card names. */
class TripleScanTrace(context:Context) {
    private val directory=File(context.filesDir,"triple-trace")
    private val dropped=AtomicLong()
    val droppedEvents:Long get()=dropped.get()
    private val worker=ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(64)) { _,_ -> dropped.incrementAndGet() }
    fun event(message:String) {
        if(!BuildConfig.DEBUG) return
        val at=System.currentTimeMillis()
        EdScanPerf.event(message)
        worker.execute {
            runCatching {
                directory.mkdirs();val file=File(directory,"current.log")
                if(file.length()>8*1024*1024) { val previous=File(directory,"previous.log");previous.delete();file.renameTo(previous) }
                file.appendText("$at $message\n")
            }
        }
    }
    fun close()=worker.shutdown()
}
