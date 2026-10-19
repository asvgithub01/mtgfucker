package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoGroupsDeviceTest {
    @Test fun groupKeepsIndependentPhotosAndDetectionsAcrossReloadAndGallery() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val test=InstrumentationRegistry.getInstrumentation();val ctx=test.targetContext
        val files=(1..2).map { File(ctx.cacheDir,"photo-group-$it.jpg").apply { writeBytes(test.context.assets.open("triple-card.jpg").use { it.readBytes() }) } }
        val entries=files.mapIndexed { i,file -> PhotoScanEntry(imagePath=file.path,createdAt=i.toLong(),state=PhotoScanEntry.STATE_READY) }
        entries.forEach { PhotoScanStore.save(ctx,it) }
        val before=ctx.filesDir.listFiles().orEmpty().filter { it.extension=="Json" }.associate { it.name to it.readBytes().toList() }
        try {
            PhotoGroups.join(ctx,entries.map { it.id })
            assertEquals(2,PhotoGroups.members(ctx,entries[1].id).size)
            assertEquals(1,PhotoGroups.rows(ctx,entries).size)
            PhotoScanStore.save(ctx,entries[1].copy(error="callback"))
            assertEquals(2,PhotoGroups.members(ctx,entries[0].id).size)
            ActivityScenario.launch<PhotoGalleryActivity>(Intent(ctx,PhotoGalleryActivity::class.java).putExtra(PhotoScanDetailActivity.EXTRA_PHOTO_SCAN_ID,entries[0].id)).use { scene ->
                scene.onActivity { a ->
                    val root=a.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.view.ViewGroup
                    assertTrue(root.getChildAt(2) is PhotoPriceImageView)
                    val label=root.getChildAt(1) as android.widget.TextView
                    assertEquals("1 / 2",label.text.toString())
                    (root.getChildAt(0) as android.view.ViewGroup).getChildAt(2).performClick()
                    assertEquals("2 / 2",label.text.toString())
                    assertTrue(a.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
                }
            }
            assertEquals(before,ctx.filesDir.listFiles().orEmpty().filter { it.extension=="Json" }.associate { it.name to it.readBytes().toList() })
        } finally { entries.forEach { PhotoScanStore.delete(ctx,it) };files.forEach { it.delete() } }
    }
    @Test fun actualGlideDrawableDrawsPricesAndTapOpensPhoto() {
        val test=InstrumentationRegistry.getInstrumentation();val ctx=test.targetContext
        test.runOnMainSync {
            val source=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val result=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)
            val view=PhotoPriceImageView(ctx)
            view.measure(View.MeasureSpec.makeMeasureSpec(400,1073741824),View.MeasureSpec.makeMeasureSpec(400,1073741824));view.layout(0,0,400,400)
            view.setImageDrawable(com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable(ctx.resources,source));view.resetZoom()
            view.showPrices(listOf(PhotoScanCard(cardName="Fixture",displayName="Fixture",detectedQuantity=1,printingUuid="no-price",setCode="",setName="",collectorNumber="",finish="nonfoil",imageUrl="",typeLine="",rulesText="",price=null,currency="EUR",photoBounds=PhotoCardBounds(.4f,.4f,.6f,.6f))))
            view.draw(Canvas(result));assertNotEquals(Color.RED,result.getPixel(200,200))
            var clicked=false;view.setOnClickListener { clicked=true }
            val time=android.os.SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(time,time+30,action,100f,100f,0)
                view.onTouchEvent(event);event.recycle()
            }
            assertTrue(clicked);result.recycle();source.recycle()
        }
    }
    @Test fun cameraUriIsPrivateWritableAndImportsFullImage() {
        val test=InstrumentationRegistry.getInstrumentation();val ctx=test.targetContext
        val dir=File(ctx.cacheDir,"photo_capture").apply { mkdirs() }
        val file=File(dir,"test-${java.util.UUID.randomUUID()}.jpg")
        val uri=androidx.core.content.FileProvider.getUriForFile(ctx,"${ctx.packageName}.photo-files",file)
        val bytes=test.context.assets.open("triple-card.jpg").use { it.readBytes() }
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        val entry=PhotoScanStore.importPhoto(ctx,uri)
        try { assertArrayEquals(bytes,File(entry.imagePath).readBytes());assertEquals(PhotoScanEntry.STATE_ANALYZING,entry.state) }
        finally { PhotoScanStore.delete(ctx,entry);file.delete() }
    }
}
