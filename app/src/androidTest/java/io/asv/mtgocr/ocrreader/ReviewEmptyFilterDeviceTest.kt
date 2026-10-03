package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Deterministic UI regression: fixture rows live only in Activity memory; no collection is changed. */
class ReviewEmptyFilterDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun descendants(view: View): List<View> = listOf(view)+
        if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun field(name: String)=ScanReviewActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
    private fun render(activity: ScanReviewActivity) {
        ScanReviewActivity::class.java.getDeclaredMethod("render").apply { isAccessible=true }.invoke(activity)
    }
    private fun fixture(activity: ScanReviewActivity, onlyResolved: Boolean=false) {
        field("active").setBoolean(activity,false)
        (field("handler").get(activity) as Handler).removeCallbacks(field("tick").get(activity) as Runnable)
        val entries=listOf("edscan","ocr").flatMap { source ->
            listOf(false,true).filter { !onlyResolved || it }.map { done ->
                val key="$source-$done"
                val card=CardInfo(if(source=="edscan") "Zombie Master" else "Humble Defector","","","","1").apply {
                    setCode=if(source=="edscan") "LEB" else "FRF"; languageCode="en"
                }
                ScanReviewStore.Entry(key,card,source,if(done) "reviewed" else "pending",null,"","",1,photoKey=key)
            }
        }
        field("entries").set(activity,entries)
        render(activity)
    }
    private fun chooseSource(activity: ScanReviewActivity, source: String) {
        val group=activity.findViewById<RadioGroup>(R.id.reviewSources)
        (0 until group.childCount).map { group.getChildAt(it) as RadioButton }.first { it.tag==source }.performClick()
    }
    private fun assertSource(activity: ScanReviewActivity, source: String) {
        val group=activity.findViewById<RadioGroup>(R.id.reviewSources)
        assertEquals(source,group.findViewById<RadioButton>(group.checkedRadioButtonId)?.tag)
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val image=instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.cacheDir,name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    @Test fun ocrArtDefaultsToProposalAndComparisonDoesNotEditDraft() {
        ActivityScenario.launch(ScanReviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                fixture(activity)
                val card=CardInfo("Cuombajj Witches","","","","1").apply {
                    setCode="CHR"; languageCode="en"; imgPath="file:///saved-card.jpg"
                }
                val entry=ScanReviewStore.Entry("comparison",card,"ocr","conflict",null,"candidate",
                    "Cuombajj Witches · ARN #23",1,"file:///proposed-card.jpg",candidateSet="ARN")
                field("entries").set(activity,listOf(entry)); render(activity)
                val list=activity.findViewById<ListView>(R.id.reviewList)
                val adapter=list.adapter
                fun bind(old: View?=null)=adapter.getView(0,old,list)
                var shell=bind()
                fun right()=(((shell as FrameLayout).getChildAt(0) as LinearLayout).getChildAt(1) as LinearLayout).getChildAt(1) as LinearLayout
                fun image()=((right().getChildAt(2) as FrameLayout).getChildAt(0) as ImageView)
                assertEquals("file:///proposed-card.jpg",image().getTag(R.id.card_image_cache_url))
                val header=right().getChildAt(0) as LinearLayout
                assertTrue((header.getChildAt(1) as TextView).text.contains("ARN"))
                assertEquals("https://svgs.scryfall.io/sets/arn.svg",header.getChildAt(0).tag)
                ((right().getChildAt(3) as RadioGroup).getChildAt(1) as RadioButton).performClick()
                shell=bind(shell)
                assertEquals("file:///saved-card.jpg",image().getTag(R.id.card_image_cache_url))
                ((right().getChildAt(3) as RadioGroup).getChildAt(0) as RadioButton).performClick()
                shell=bind(shell)
                assertEquals("file:///proposed-card.jpg",image().getTag(R.id.card_image_cache_url))
                assertTrue((field("drafts").get(activity) as Map<*,*>).isEmpty())
                field("entries").set(activity,listOf(entry.copy(source="edscan",visual=false)))
                render(activity); shell=bind(shell)
                assertEquals(View.GONE,right().getChildAt(3).visibility)
                assertEquals("file:///saved-card.jpg",image().getTag(R.id.card_image_cache_url))
            }
        }
    }
    @Test fun historyFillsScreenAndSeparatesResultsAndPaddingAligns() {
        ActivityScenario.launch(ScanReviewActivity::class.java).use { scenario ->
            val deadline=System.currentTimeMillis()+10000
            var loading=true
            while(loading && System.currentTimeMillis()<deadline) {
                scenario.onActivity { loading=field("historyLoading").getBoolean(it) }
                if(loading) Thread.sleep(50)
            }
            assertFalse(loading)
            scenario.onActivity { activity ->
                fixture(activity)
                @Suppress("UNCHECKED_CAST")
                val entries=field("entries").get(activity) as List<ScanReviewStore.Entry>
                field("entries").set(activity,entries.map { it.copy(ocrCompleted=true,language="en") })
                val batch=ReliableReviewHistory.Batch("test",System.currentTimeMillis(),2,listOf(
                    ReliableReviewHistory.Item("one","Cuombajj Witches","skipped","identity",null),
                    ReliableReviewHistory.Item("two","Zombie Master","applied","ok",false)))
                field("reliableHistory").set(activity,listOf(batch,batch.copy(id="other",at=batch.at-10000)))
                val status=field("analysisStatus").get(activity) as TextView
                status.setText(R.string.review_processing_finished); status.visibility=View.VISIBLE
                assertTrue(status.paddingStart>0); assertEquals(status.paddingStart,status.paddingEnd)
                render(activity)
                val list=activity.findViewById<ListView>(R.id.reviewList)
                val shell=list.adapter.getView(0,null,list) as FrameLayout
                assertEquals(0,shell.paddingStart); assertEquals(0,shell.paddingEnd)
                val header=((shell.getChildAt(0) as LinearLayout).getChildAt(0) as LinearLayout).getChildAt(0) as TextView
                assertTrue(header.text.contains(activity.getString(R.string.review_ocr_no_title)))
                assertFalse(header.text.contains(activity.getString(R.string.review_pending)))
                descendants(activity.window.decorView).filterIsInstance<ImageButton>()
                    .first { it.contentDescription==activity.getString(R.string.review_history) }.performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                @Suppress("UNCHECKED_CAST")
                val dialog=(field("historyDialogs").get(activity) as Set<androidx.appcompat.app.AlertDialog>).single()
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,dialog.window!!.attributes.height)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,dialog.window!!.attributes.width)
                val list=descendants(dialog.window!!.decorView).filterIsInstance<ListView>().single()
                assertTrue(list.height>0); assertNotNull(list.divider); assertTrue(list.dividerHeight>0)
                assertTrue("History list must fill spare vertical space",list.height>dialog.window!!.decorView.height/2)
                assertEquals(2,list.adapter.count)
            }
            screenshot("review-ui-history.png")
            scenario.onActivity { activity ->
                @Suppress("UNCHECKED_CAST")
                val dialog=(field("historyDialogs").get(activity) as Set<androidx.appcompat.app.AlertDialog>).single()
                val list=descendants(dialog.window!!.decorView).filterIsInstance<ListView>().single()
                list.performItemClick(list.adapter.getView(0,null,list),0,0)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                @Suppress("UNCHECKED_CAST")
                val dialogs=field("historyDialogs").get(activity) as Set<androidx.appcompat.app.AlertDialog>
                assertEquals(2,dialogs.size)
                val details=dialogs.last()
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,details.window!!.attributes.height)
                val list=descendants(details.window!!.decorView).filterIsInstance<ListView>().single()
                assertEquals(4,list.adapter.count) // Summary, two separated resolutions, backup notice.
                assertNotNull(list.divider)
            }
            screenshot("review-ui-history-detail.png")
        }
    }
    @Test fun emptyPendingTabCanBeToggledAndRestored() {
        ActivityScenario.launch(ScanReviewActivity::class.java).use { scenario ->
            scenario.onActivity { fixture(it,true) }
            repeat(3) {
                scenario.onActivity { activity ->
                    val tabs=activity.findViewById<TabLayout>(R.id.reviewTabs)
                    val list=activity.findViewById<ListView>(R.id.reviewList)
                    tabs.getTabAt(1)!!.select(); assertEquals(2,list.adapter.count)
                    tabs.getTabAt(0)!!.select(); assertEquals(0,list.adapter.count)
                    assertEquals(AdapterView.INVALID_ROW_ID,list.adapter.getItemId(0))
                }
                instrumentation.waitForIdleSync()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                fixture(activity,true)
                assertEquals(0,activity.findViewById<TabLayout>(R.id.reviewTabs).selectedTabPosition)
                assertEquals(0,activity.findViewById<ListView>(R.id.reviewList).adapter.count)
            }
        }
    }
    @Test fun tabsRememberIndependentFiltersAndCardsToggleWithoutExtraButton() {
        ActivityScenario.launch(ScanReviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                fixture(activity)
                val list=activity.findViewById<ListView>(R.id.reviewList)
                val tabs=activity.findViewById<TabLayout>(R.id.reviewTabs)
                assertEquals(2,list.adapter.count)
                chooseSource(activity,"edscan"); assertEquals(1,list.adapter.count)
                tabs.getTabAt(1)!!.select(); assertSource(activity,"all"); assertEquals(2,list.adapter.count)
                chooseSource(activity,"ocr"); assertEquals(1,list.adapter.count)
                tabs.getTabAt(0)!!.select(); assertSource(activity,"edscan"); assertEquals(1,list.adapter.count)
                val views=descendants(activity.window.decorView)
                assertEquals(1,views.filterIsInstance<CardArtBackgroundView>().size)
                assertFalse(views.filterIsInstance<Button>().any { it.text==activity.getString(R.string.review_analyse_all) || it.text==activity.getString(R.string.review_history) })
                val shell=list.adapter.getView(0,null,list) as FrameLayout
                assertEquals(1,shell.childCount)
                val card=shell.getChildAt(0) as LinearLayout
                val header=(card.getChildAt(0) as LinearLayout).getChildAt(0) as TextView
                assertNotNull(header.compoundDrawablesRelative[2])
                assertEquals(View.VISIBLE,card.getChildAt(1).visibility)
                card.performClick()
                val collapsed=list.adapter.getView(0,shell,list) as FrameLayout
                assertEquals(View.GONE,(collapsed.getChildAt(0) as LinearLayout).getChildAt(1).visibility)
            }
            screenshot("review-ui-pending.png")
            scenario.onActivity { it.findViewById<TabLayout>(R.id.reviewTabs).getTabAt(1)!!.select() }
            scenario.recreate()
            scenario.onActivity { activity ->
                fixture(activity)
                assertEquals(1,activity.findViewById<TabLayout>(R.id.reviewTabs).selectedTabPosition)
                assertSource(activity,"ocr")
                assertEquals(1,activity.findViewById<ListView>(R.id.reviewList).adapter.count)
                activity.findViewById<TabLayout>(R.id.reviewTabs).getTabAt(0)!!.select()
                assertSource(activity,"edscan")
            }
            screenshot("review-ui-expanded.png")
            scenario.onActivity { activity ->
                descendants(activity.window.decorView).filterIsInstance<ImageButton>()
                    .first { it.contentDescription==activity.getString(R.string.review_rules_title) }.performClick()
            }
            screenshot("review-ui-info.png")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        }
    }
}
