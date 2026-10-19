package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PhotoEdScannerTest {
    @Test fun recognizesSeparateCopiesWithoutChangingCollection() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ctx=instrumentation.targetContext
        fun hashes()=ctx.filesDir.listFiles().orEmpty().filter { it.extension.equals("Json",true) }
            .associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val before=hashes()
        val card=instrumentation.context.assets.open("triple-card.jpg").use { BitmapFactory.decodeStream(it) }
        val bitmap=Bitmap.createBitmap(card.width*2+120,card.height+80,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.DKGRAY);drawBitmap(card,30f,40f,null);drawBitmap(card,card.width+90f,40f,null) }
        val file=File(ctx.cacheDir,"photo-edscan-fixture.jpg")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,98,it) }
            val latch=CountDownLatch(1)
            var cards=emptyList<PhotoScanCard>();var error:Throwable?=null
            PhotoCardAnalyzer.recognize(ctx,file.absolutePath) { found,failure -> cards=found;error=failure;latch.countDown() }
            assertTrue("Analysis timeout",latch.await(240,TimeUnit.SECONDS))
            assertNull(error)
            assertEquals("Two physical copies should remain separate",2,cards.size)
            assertTrue(cards.all { it.photoBounds != null && it.detectedQuantity==1 && it.finish=="nonfoil" })
            assertEquals(cards[0].printingUuid,cards[1].printingUuid)
            assertFalse(cards[0].photoBounds!!.overlaps(cards[1].photoBounds!!))
            assertEquals(before,hashes())
        } finally { file.delete();bitmap.recycle();card.recycle() }
    }
    @Test fun priceOverlayUsesFitCenterImageCoordinates() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val ctx=instrumentation.targetContext
            val source=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val result=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)
            val view=PhotoPriceImageView(ctx).apply {
                scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
                setImageBitmap(source)
                measure(android.view.View.MeasureSpec.makeMeasureSpec(400,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(400,1073741824))
                layout(0,0,400,400)
                showPrices(listOf(PhotoScanCard(cardName="Fixture",displayName="Fixture",detectedQuantity=1,
                    printingUuid="no-price",setCode="set",setName="Set",collectorNumber="1",finish="nonfoil",
                    imageUrl="",typeLine="",rulesText="",price=null,currency="EUR",
                    photoBounds=PhotoCardBounds(.2f,.2f,.4f,.4f))))
            }
            view.draw(Canvas(result))
            assertEquals(Color.TRANSPARENT,result.getPixel(50,120))
            assertEquals(Color.RED,result.getPixel(250,120))
            assertNotEquals(Color.RED,result.getPixel(160,120))
            result.recycle();source.recycle()
        }
    }
    @Test fun existingPhotoCanRequestEdScannerWithoutEditingCollection() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        for (command in listOf("input keyevent KEYCODE_WAKEUP", "wm dismiss-keyguard")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        val ctx=instrumentation.targetContext
        val before=ctx.filesDir.listFiles().orEmpty().filter { it.extension.equals("Json",true) }
            .associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val file=File(ctx.cacheDir,"existing-photo-fixture.jpg").apply {
            writeBytes(instrumentation.context.assets.open("triple-card.jpg").use { it.readBytes() })
        }
        val entry=PhotoScanEntry(imagePath=file.absolutePath,state=PhotoScanEntry.STATE_READY)
        PhotoScanStore.save(ctx,entry)
        try {
            androidx.test.core.app.ActivityScenario.launch<PhotoScanDetailActivity>(android.content.Intent(ctx,PhotoScanDetailActivity::class.java)
                .putExtra(PhotoScanDetailActivity.EXTRA_PHOTO_SCAN_ID,entry.id)).use { scene ->
                scene.onActivity { activity ->
                    assertTrue(activity.findViewById<android.view.View>(R.id.imgPhotoDetail) is PhotoPriceImageView)
                    activity.findViewById<android.widget.Button>(R.id.btnPhotoEdScan).performClick()
                }
                instrumentation.waitForIdleSync()
                val ui=instrumentation.uiAutomation
                ui.serviceInfo=ui.serviceInfo.apply { flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
                var positive:android.view.accessibility.AccessibilityNodeInfo?=null
                val deadline=android.os.SystemClock.elapsedRealtime()+5000
                while (positive==null && android.os.SystemClock.elapsedRealtime()<deadline) {
                    positive=ui.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("android:id/button1")?.firstOrNull()
                    if (positive==null) Thread.sleep(100)
                }
                assertNotNull("Confirmation dialog must be accessible",positive)
                assertTrue(positive!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                instrumentation.waitForIdleSync()
            }
            assertEquals(PhotoScanEntry.STATE_ANALYZING,PhotoScanStore.find(ctx,entry.id)?.state)
            val after=ctx.filesDir.listFiles().orEmpty().filter { it.extension.equals("Json",true) }
                .associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
            assertEquals(before,after)
        } finally { PhotoScanStore.delete(ctx,entry);file.delete() }
    }
    @Test fun exifRotationMatchesUprightCoordinates() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(ctx.cacheDir,"photo-exif-test.jpg")
        val bitmap=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) }
            androidx.exifinterface.media.ExifInterface(file).apply {
                setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,"6");saveAttributes()
            }
            val decoded=PhotoCardAnalyzer.decode(file.absolutePath)
            assertEquals(200,decoded.width);assertEquals(100,decoded.height);decoded.recycle()
        } finally { file.delete();bitmap.recycle() }
    }
}
