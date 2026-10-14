package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Read-only navigation of the real library; never clicks inventory mutations. */
class HomeNavigationDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun hashes()=LibraryCatalog.libraries(context).map { File(context.filesDir,it.fileName) }
        .filter { it.isFile }.associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
    private fun idle() { Thread.sleep(600);instrumentation.waitForIdleSync() }
    @Test fun everyOwnedGalleryPageHasMeasuredMenu() {
        val before=hashes()
        val cards=LegacyCollectionStore.cards(context).filter { !it.imgPath.isNullOrBlank() }
        assertTrue(cards.isNotEmpty())
        val token=CardGalleryStore.put(cards.map { CardGalleryStore.Page(it.collectionItemId,it.name,it.collectionItemId,
            it.imgPath,it.name,it.setCode.orEmpty(),it.collectorNumber.orEmpty(),"",it.finish.orEmpty()) })
        ActivityScenario.launch<CardImageActivity>(Intent(context,CardImageActivity::class.java)
            .putExtra(CardImageActivity.EXTRA_GALLERY_TOKEN,token)).use { scenario ->
            for(index in cards.indices) {
                scenario.onActivity { it.findViewById<ViewPager2>(R.id.galleryPager).setCurrentItem(index,false) }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val button=activity.findViewById<View>(R.id.btnCardImageDetails)
                    assertEquals("Menu for "+cards[index].name,View.VISIBLE,button.visibility)
                    assertTrue("Measured menu for "+cards[index].name,button.width>= (48*activity.resources.displayMetrics.density).toInt())
                }
            }
        }
        assertEquals(before,hashes())
    }
    @Test fun homeKeepsAdapterAndExactScrollAfterGalleryAndReview() {
        val before=hashes()
        ActivityScenario.launch<OcrCaptureActivity>(Intent(context,OcrCaptureActivity::class.java)
            .putExtra(App.INTENT_PERSISTOR_MODE,"0")).use { scenario ->
            idle()
            var adapter: RecyclerView.Adapter<*>?=null
            var position=0;var offset=0
            scenario.onActivity { activity ->
                val list=activity.findViewById<RecyclerView>(R.id.my_recycler_view)
                (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(12, -17)
            }
            idle()
            scenario.onActivity { activity ->
                val list=activity.findViewById<RecyclerView>(R.id.my_recycler_view)
                val layout=list.layoutManager as LinearLayoutManager
                adapter=list.adapter;position=layout.findFirstVisibleItemPosition()
                offset=layout.getDecoratedLeft(requireNotNull(layout.findViewByPosition(position)))-list.paddingLeft
                val cards=(adapter as MyAdapter).let { a -> (0 until a.itemCount).map { a.getItem(it) } }
                assertTrue(activity.openCardGallery(cards[position],cards))
            }
            idle();instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);idle()
            fun checkHome() { scenario.onActivity { activity ->
                val list=activity.findViewById<RecyclerView>(R.id.my_recycler_view)
                val layout=list.layoutManager as LinearLayoutManager
                assertSame("Read-only return must retain adapter",adapter,list.adapter)
                assertEquals(position,layout.findFirstVisibleItemPosition())
                assertEquals(offset,layout.getDecoratedLeft(requireNotNull(layout.findViewByPosition(position)))-list.paddingLeft)
            } }
            checkHome()
            scenario.onActivity { it.startActivity(Intent(it,ScanReviewActivity::class.java).putExtra(ScanReviewActivity.EXTRA_GROUP,"__empty_navigation_test__")) }
            idle();instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);idle()
            checkHome()
        }
        assertEquals("Navigation must not write any library",before,hashes())
    }
    @Test fun postprocessingInheritsActualHomeGroupAndKeepsEmptyScopeEmpty() {
        val before=hashes()
        val all=ScanReviewStore.read(context,LibraryCatalog.activeFile(context))
        val group=all.flatMap { it.card.groups }.filter { it.startsWith("Triple ") }.sorted().last()
        val expected=all.filter { group in it.card.groups }.map { it.photoKey }.toSet()
        assertTrue(expected.isNotEmpty())
        val monitor=instrumentation.addMonitor(ScanReviewActivity::class.java.name,null,false)
        ActivityScenario.launch<OcrCaptureActivity>(Intent(context,OcrCaptureActivity::class.java)
            .putExtra(App.INTENT_PERSISTOR_MODE,"0")).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                val spinner=activity.findViewById<android.widget.Spinner>(R.id.spinnerCollectionFilter)
                val index=(0 until spinner.count).first { n ->
                    val option=spinner.getItemAtPosition(n)
                    option.javaClass.getDeclaredField("key").apply { isAccessible=true }.get(option)=="group:"+group
                }
                spinner.setSelection(index)
            }
            idle()
            scenario.onActivity { it.findViewById<View>(R.id.btnCollectionActions).performClick() }
            idle()
            instrumentation.runOnMainSync {
                fun descendants(view: View): List<View> = listOf(view) +
                    if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                val lists=android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { descendants(it) }.filterIsInstance<android.widget.ListView>()
                val label=context.getString(R.string.postprocess_scans)
                val menu=lists.first { list -> (0 until list.count).any { list.getItemAtPosition(it).toString()==label } }
                val index=(0 until menu.count).first { menu.getItemAtPosition(it).toString()==label }
                assertTrue(menu.performItemClick(menu.getChildAt(index-menu.firstVisiblePosition),index,menu.getItemIdAtPosition(index)))
            }
            val review=monitor.waitForActivityWithTimeout(10000) as ScanReviewActivity
            assertEquals(group,review.intent.getStringExtra(ScanReviewActivity.EXTRA_GROUP))
            val field=ScanReviewActivity::class.java.getDeclaredField("entries").apply { isAccessible=true }
            var keys=emptySet<String>()
            repeat(20) {
                idle()
                instrumentation.runOnMainSync {
                    @Suppress("UNCHECKED_CAST")
                    val entries=field.get(review) as List<ScanReviewStore.Entry>
                    assertTrue(entries.all { group in it.card.groups })
                    keys=entries.map { it.photoKey }.toSet()
                }
                if(keys==expected) return@repeat
            }
            assertEquals(expected,keys)
            instrumentation.runOnMainSync { review.finish() };idle()
        }
        instrumentation.removeMonitor(monitor)
        ActivityScenario.launch<ScanReviewActivity>(Intent(context,ScanReviewActivity::class.java)
            .putExtra(ScanReviewActivity.EXTRA_GROUP,"__no_such_group__")).use { scenario ->
            Thread.sleep(3500);idle()
            scenario.onActivity { activity ->
                val entries=ScanReviewActivity::class.java.getDeclaredField("entries").apply { isAccessible=true }.get(activity) as List<*>
                assertTrue("An empty group must not fall back to every library photo",entries.isEmpty())
            }
        }
        assertEquals(before,hashes())
    }

}
