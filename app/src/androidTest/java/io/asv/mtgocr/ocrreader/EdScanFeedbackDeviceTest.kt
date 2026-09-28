package io.asv.mtgocr.ocrreader

import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.collectorvision.EdScanInferencePriority
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.TextView
import android.view.ViewGroup

/** No inventory writes: benchmark output is confined to a temporary noBackup file. */
class EdScanFeedbackDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun comparesBufferedSerializationWithoutChangingInventory() {
        val owner = Any(); EdScanInferencePriority.acquire(owner)
        val file = File(context.filesDir, LibraryCatalog.activeFile(context))
        val output = File(context.noBackupFilesDir, "edscan-buffer-benchmark.tmp")
        try {
            var expected: ByteArray? = null
            for (buffered in listOf(false,true,true,false)) {
                var start = SystemClock.elapsedRealtime()
                val collection = ObjectInputStream(if(buffered) BufferedInputStream(FileInputStream(file)) else FileInputStream(file)).use { it.readObject() as Biblio }
                val readMs = SystemClock.elapsedRealtime()-start
                start = SystemClock.elapsedRealtime()
                ObjectOutputStream(if(buffered) BufferedOutputStream(FileOutputStream(output)) else FileOutputStream(output)).use { it.writeObject(collection) }
                val writeMs = SystemClock.elapsedRealtime()-start
                val bytes=output.readBytes()
                if(expected==null) expected=bytes else assertArrayEquals(expected,bytes)
                android.util.Log.i("EdScanFeedbackTest", "buffered=$buffered readMs=$readMs writeMs=$writeMs bytes=${bytes.size} rows=${collection.cards.size}")
            }
        } finally { output.delete(); EdScanInferencePriority.release(owner) }
    }
    @Test fun exactPrintingLookupRetainsAvailableLocalPrices() {
        val id="3c3863a1-7c9f-4884-8915-84d088ce231c"
        val dao=CardDatabase.get(context).cardDao()
        val printing=dao.printingByScryfallId(id)
        assertNotNull("Karn must already be cached for this offline test",printing)
        val available=dao.snapshotPricesFor(listOf(printing!!.uuid)).isNotEmpty() || dao.pricesFor(listOf(printing.uuid)).isNotEmpty()
        assertTrue("Expected an existing local price",available)
        val done=CountDownLatch(1)
        var error:Throwable?=null;var priced=false
        val start=SystemClock.elapsedRealtime()
        CardRepository.get(context).loadCollectorVisionPrinting(id) { options,failure ->
            error=failure;priced=options.any { it.price!=null };done.countDown()
        }
        assertTrue(done.await(15,TimeUnit.SECONDS)); assertNull(error); assertTrue(priced)
        android.util.Log.i("EdScanFeedbackTest","localLookupMs=${SystemClock.elapsedRealtime()-start}")
    }
    @Test fun pendingTransitionDoesNotReplacePreviouslySavedTile() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val themed=android.view.ContextThemeWrapper(context,androidx.appcompat.R.style.Theme_AppCompat)
            val view=RecentScansView(themed)
            val first=CardInfo("first","1 EUR","","","1")
            val second=CardInfo("second","2 EUR","","","1")
            val listener=RecentScansView.Listener { }
            view.beginPending("one")
            val red=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            view.setPendingPreview("one",red)
            view.completePending("one",first.collectionItemId,false)
            view.submit(listOf(first),listener)
            view.beginPending("two")
            val blue=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            view.setPendingPreview("two",blue)
            fun layout() { view.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY));view.layout(0,0,800,200) }
            layout()
            assertEquals(2,view.adapter!!.itemCount)
            val previous=view.findViewHolderForAdapterPosition(1)!!.itemView as ViewGroup
            assertTrue((previous.getChildAt(1) as TextView).text.toString().contains("1"))
            view.completePending("two",second.collectionItemId,false)
            view.submit(listOf(first,second),listener);layout()
            assertEquals(2,view.adapter!!.itemCount)
            for(i in 0..1) {
                val row=view.findViewHolderForAdapterPosition(i)!!.itemView as ViewGroup
                assertFalse((row.getChildAt(1) as TextView).text.toString().contains("…"))
            }
        }
    }
}
