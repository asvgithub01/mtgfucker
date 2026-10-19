package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoPickerDeviceTest {
    private val test get()=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=test.targetContext
    private fun card(uuid:String="first",finish:String="nonfoil")=PhotoScanCard(cardName="Fixture",displayName="Fixture",detectedQuantity=1,
        printingUuid=uuid,setCode="SET",setName="Fixture edition",collectorNumber="1",finish=finish,imageUrl="https://photo-picker-fixture.invalid/$uuid.jpg",typeLine="",rulesText="",price=null,currency="EUR")
    @Test fun draftCanBeDiscardedAndOnlyExplicitAddSavesChosenEdition() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val photo=PhotoScanEntry(imagePath=File(ctx.cacheDir,"picker-no-image").path,state=PhotoScanEntry.STATE_READY)
        PhotoScanStore.save(ctx,photo)
        val images=listOf("first","second").map { uuid -> File(File(ctx.filesDir,"card_images"),StorageData.hash("https://photo-picker-fixture.invalid/$uuid.jpg")).apply {
            parentFile!!.mkdirs();writeBytes(test.context.assets.open("triple-card.jpg").use { it.readBytes() })
        } }
        val before=ctx.filesDir.listFiles().orEmpty().filter { it.extension=="Json" }.associate { it.name to it.readBytes().toList() }
        try {
            ActivityScenario.launch<CardSearchActivity>(Intent(ctx,CardSearchActivity::class.java).putExtra(CardSearchActivity.EXTRA_PHOTO_ID,photo.id)).use { scene ->
                scene.onActivity { a ->
                    assertNotNull(a.findViewById<View>(R.id.txtCardSearch))
                    val preview=a.findViewById<PhotoCardPreview>(R.id.photoDraft)
                    assertEquals(View.GONE,preview.visibility)
                    preview.show(card().toEditionOption())
                    assertEquals(View.VISIBLE,preview.visibility)
                    assertTrue(PhotoScanStore.find(ctx,photo.id)!!.cards.isEmpty())
                    assertNotNull(a.findViewById<View>(R.id.photoDraftImage))
                    assertNotNull(a.findViewById<View>(R.id.photoDraftPrice))
                    assertTrue(a.findViewById<TextView>(R.id.photoDraftEdition).text.contains("SET"))
                    a.findViewById<View>(R.id.photoDraftDiscard).performClick()
                    assertEquals(View.GONE,preview.visibility)
                    assertTrue(PhotoScanStore.find(ctx,photo.id)!!.cards.isEmpty())
                    preview.show(card("second","foil").toEditionOption())
                }
                test.waitForIdleSync()
                Thread.sleep(300)
                test.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(ctx.cacheDir,"photo-picker-preview.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                }
                scene.onActivity { a -> a.findViewById<View>(R.id.photoDraftAdd).performClick() }
                val deadline=android.os.SystemClock.elapsedRealtime()+5000
                while(PhotoScanStore.find(ctx,photo.id)!!.cards.isEmpty() && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50)
                val saved=PhotoScanStore.find(ctx,photo.id)!!.cards.single()
                assertEquals("second",saved.printingUuid);assertEquals("foil",saved.finish);assertNull(saved.photoBounds)
            }
            assertEquals(before,ctx.filesDir.listFiles().orEmpty().filter { it.extension=="Json" }.associate { it.name to it.readBytes().toList() })
        } finally { PhotoScanStore.delete(ctx,photo);images.forEach { it.delete() } }
    }
    @Test fun addNeverResurrectsDeletedPhotoOrOverwritesAnalysis() {
        val photo=PhotoScanEntry(imagePath="missing",state=PhotoScanEntry.STATE_ANALYZING)
        PhotoScanStore.save(ctx,photo)
        try { assertFalse(PhotoScanStore.addManual(ctx,photo.id,card()));assertTrue(PhotoScanStore.find(ctx,photo.id)!!.cards.isEmpty()) }
        finally { PhotoScanStore.delete(ctx,photo) }
        assertFalse(PhotoScanStore.addManual(ctx,photo.id,card()));assertNull(PhotoScanStore.find(ctx,photo.id))
    }
    @Test fun checkboxesTrackIdsAcrossRebindWithoutOpeningItems() {
        ActivityScenario.launch<CardSearchActivity>(Intent(ctx,CardSearchActivity::class.java)).use { scene ->
            scene.onActivity { a ->
                var opened=false
                val adapter=PhotoScanLibraryAdapter({opened=true},{})
                val first=PhotoScanEntry(imagePath="",createdAt=1,state=PhotoScanEntry.STATE_READY)
                val second=PhotoScanEntry(imagePath="",createdAt=2,state=PhotoScanEntry.STATE_READY)
                adapter.submit(listOf(first,second))
                val parent=android.widget.FrameLayout(a)
                val holder=adapter.onCreateViewHolder(parent,0)
                adapter.onBindViewHolder(holder,0)
                holder.itemView.findViewById<CheckBox>(R.id.checkPhotoGroup).performClick()
                assertEquals(listOf(second.id),adapter.selectedIds());assertFalse(opened)
                adapter.onBindViewHolder(holder,1)
                assertFalse(holder.itemView.findViewById<CheckBox>(R.id.checkPhotoGroup).isChecked)
                holder.itemView.findViewById<CheckBox>(R.id.checkPhotoGroup).performClick()
                assertEquals(2,adapter.selectedIds().size)
                adapter.submit(listOf(first));assertEquals(listOf(first.id),adapter.selectedIds())
                adapter.clearSelection();assertTrue(adapter.selectedIds().isEmpty())
            }
        }
    }
}
