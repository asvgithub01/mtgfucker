package io.asv.mtgocr.ocrreader

import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ListView
import android.widget.AdapterView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only regression for a library whose remaining photos have all been reviewed. */
class ReviewEmptyFilterDeviceTest {
    private fun descendants(view: View): List<View> = listOf(view)+
        if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun emptyPendingFilterCanBeToggledAndRestored() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=LibraryCatalog.active(context).id
        val requested=InstrumentationRegistry.getArguments().getString("reviewLibraryId")
        try {
            if(requested!=null) assertEquals(requested,LibraryCatalog.select(context,requested).id)
            checkFilter()
        } finally { if(requested!=null) LibraryCatalog.select(context,original) }
    }
    private fun checkFilter() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val entries=ScanReviewStore.read(context,LibraryCatalog.activeFile(context))
        assumeTrue(entries.isNotEmpty() && entries.all { it.state=="reviewed" })
        ActivityScenario.launch(ScanReviewActivity::class.java).use { scenario ->
            var loaded=false
            val deadline=System.currentTimeMillis()+20000
            while(!loaded && System.currentTimeMillis()<deadline) {
                scenario.onActivity { activity -> loaded=descendants(activity.window.decorView).filterIsInstance<ListView>().first().adapter.count>0 }
                if(!loaded) Thread.sleep(100)
            }
            assertTrue("Review data must load before testing the nonempty-to-empty transition",loaded)
            repeat(3) {
                scenario.onActivity { activity ->
                    val views=descendants(activity.window.decorView)
                    val filter=views.filterIsInstance<CheckBox>().first { it.text.toString()==activity.getString(R.string.review_pending_only) }
                    val list=views.filterIsInstance<ListView>().first()
                    filter.isChecked=true
                    assertEquals(0,list.adapter.count)
                    assertEquals(AdapterView.INVALID_ROW_ID,list.adapter.getItemId(0))
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val views=descendants(activity.window.decorView)
                    views.filterIsInstance<CheckBox>().first { it.text.toString()==activity.getString(R.string.review_pending_only) }.isChecked=false
                    assertTrue(views.filterIsInstance<ListView>().first().adapter.count>0)
                }
            }
            scenario.onActivity { activity -> descendants(activity.window.decorView).filterIsInstance<CheckBox>()
                .first { it.text.toString()==activity.getString(R.string.review_pending_only) }.isChecked=true }
            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val views=descendants(activity.window.decorView)
                assertTrue(views.filterIsInstance<CheckBox>().first { it.text.toString()==activity.getString(R.string.review_pending_only) }.isChecked)
                assertEquals(0,views.filterIsInstance<ListView>().first().adapter.count)
            }
        }
    }
}
