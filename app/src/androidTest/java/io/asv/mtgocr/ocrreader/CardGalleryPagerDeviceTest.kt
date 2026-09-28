package io.asv.mtgocr.ocrreader

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.junit.Assert.*
import org.junit.Test

class CardGalleryPagerDeviceTest {
    @Test fun nativePagerNavigatesWithoutLoadedImagesAndRestoresPosition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, CardImageActivity::class.java)
            .putStringArrayListExtra(CardImageActivity.EXTRA_EDITION_IMAGE_URLS,
                arrayListOf("file:///missing1.jpg", "file:///missing2.jpg", "file:///missing3.jpg"))
            .putStringArrayListExtra(CardImageActivity.EXTRA_EDITION_LABELS, arrayListOf("One", "Two", "Three"))
            .putStringArrayListExtra(CardImageActivity.EXTRA_EDITION_SET_CODES, arrayListOf("", "", ""))
            .putStringArrayListExtra(CardImageActivity.EXTRA_EDITION_COLLECTOR_NUMBERS, arrayListOf("", "", ""))
            .putStringArrayListExtra(CardImageActivity.EXTRA_EDITION_PRICES, arrayListOf("1 EUR", "2 EUR", "3 EUR"))
            .putExtra(CardImageActivity.EXTRA_EDITION_INDEX, 1)
        ActivityScenario.launch<CardImageActivity>(intent).use { scenario ->
            scenario.onActivity { assertEquals(1, it.findViewById<ViewPager2>(R.id.galleryPager).currentItem) }
            val down = android.os.SystemClock.uptimeMillis()
            for (step in 0..12) {
                scenario.onActivity {
                    val pager = it.findViewById<ViewPager2>(R.id.galleryPager)
                    val action = if (step == 0) android.view.MotionEvent.ACTION_DOWN
                        else if (step == 12) android.view.MotionEvent.ACTION_UP else android.view.MotionEvent.ACTION_MOVE
                    val event = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(),
                        action, pager.width*(.85f-.7f*step/12), pager.height*.5f, 0)
                    pager.dispatchTouchEvent(event); event.recycle()
                }
                Thread.sleep(20)
            }
            Thread.sleep(600)
            scenario.onActivity { assertEquals(2, it.findViewById<ViewPager2>(R.id.galleryPager).currentItem) }
            scenario.recreate()
            scenario.onActivity {
                assertEquals(2, it.findViewById<ViewPager2>(R.id.galleryPager).currentItem)
                val strip = it.findViewById<RecyclerView>(R.id.galleryThumbnails)
                assertEquals(3, strip.adapter!!.itemCount)
                strip.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
                assertEquals(0, it.findViewById<ViewPager2>(R.id.galleryPager).currentItem)
            }
        }
    }
}
