package io.asv.mtgocr.ocrreader
import android.content.Intent
import android.view.View
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** UI checks never click edition/language/quantity/delete mutations on the real inventory. */
class LibraryGalleryUiDeviceTest {
    private fun snapshot(context:android.content.Context)=java.security.MessageDigest.getInstance("SHA-256").digest(File(context.filesDir,LibraryCatalog.activeFile(context)).readBytes()).toList()
    @Test fun galleryMenuClosePriceAndSymbolAndReadOnlyPopup() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val before=snapshot(context)
        val card=LegacyCollectionStore.cards(context).first { !it.printingUuid.isNullOrBlank() && !it.imgPath.isNullOrBlank() }
        val token=CardGalleryStore.put(listOf(CardGalleryStore.Page(card.collectionItemId,card.name,card.collectionItemId,card.imgPath,card.name,card.setCode,card.collectorNumber,"1 EUR",card.finish)))
        ActivityScenario.launch<CardImageActivity>(Intent(context,CardImageActivity::class.java).putExtra(CardImageActivity.EXTRA_GALLERY_TOKEN,token)).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.btnCloseCardImage) is ImageButton)
                assertTrue(activity.findViewById<View>(R.id.gallerySetSymbol).isEnabled)
                assertEquals(View.VISIBLE,activity.findViewById<View>(R.id.btnCardImageDetails).visibility)
                val menu=activity.findViewById<View>(R.id.btnCardImageDetails)
                assertTrue("Action must have a real touch target on the first page",menu.width >= (48*activity.resources.displayMetrics.density).toInt())
                val price=activity.findViewById<TextView>(R.id.txtCardImagePrice)
                val language=activity.findViewById<Spinner>(R.id.cardLanguageSpinner)
                val a=IntArray(2);val b=IntArray(2);price.getLocationOnScreen(a);language.getLocationOnScreen(b)
                assertTrue("Price below language",a[1]>=b[1])
                activity.findViewById<View>(R.id.btnCardImageDetails).performClick()
            }
            instrumentation.waitForIdleSync()
            val image=instrumentation.uiAutomation.takeScreenshot()
            File(context.cacheDir,"gallery-ui-proof.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        }
        assertEquals(before,snapshot(context))
        ActivityScenario.launch<Main2Activity>(Intent(context,Main2Activity::class.java).putExtra(Main2Activity.EXTRA_CARD_NAME,card.name)
            .putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID,card.collectionItemId).putExtra(Main2Activity.EXTRA_AS_POPUP,true)).use { scenario ->
            Thread.sleep(2500);instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val attrs=activity.theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsFloating))
                assertFalse(attrs.getBoolean(0,false));attrs.recycle()
                assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, activity.window.attributes.width)
                assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, activity.window.attributes.height)
                assertNotNull(activity.findViewById<View>(R.id.btnDetailMenu))
                for(layout in listOf(R.layout.card_grid_item,R.layout.card_art_item)) {
                    assertTrue(activity.layoutInflater.inflate(layout,null).findViewById<View>(R.id.btnDeleteCard) is CheckBox)
                }
            }
            val image=instrumentation.uiAutomation.takeScreenshot()
            File(context.cacheDir,"detail-popup-proof.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        }
        assertEquals("Read-only popup must not rewrite user collection",before,snapshot(context))
    }
    @Test fun priceAnalysisPreservesInventoryAndKeepsUnavailableLanguagesUnknown() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val before=snapshot(context);val analyzer=ReviewPriceAnalyzer(context)
        val latch=CountDownLatch(1);var result:ReviewPriceRanges.Result?=null;var failure:Throwable?=null
        try {
            analyzer.analyze("__ui-test-stingerquill","Stingerquill Charm","nonfoil") { value,error->result=value;failure=error;latch.countDown() }
            assertTrue(latch.await(60,TimeUnit.SECONDS));assertNull(failure)
            assertTrue(requireNotNull(result).priced>=2);assertNotNull(result?.editions)
            assertTrue(requireNotNull(result).total>=requireNotNull(result).priced)
            assertEquals(before,snapshot(context))
        } finally { analyzer.close();context.getSharedPreferences("review_price_ranges_v1",0).edit().remove("uiteststingerquill|nonfoil").commit() }
    }
}
