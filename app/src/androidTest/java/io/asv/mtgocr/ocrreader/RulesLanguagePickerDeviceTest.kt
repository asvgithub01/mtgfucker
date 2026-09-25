package io.asv.mtgocr.ocrreader

import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.CardImageVariant
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.test.platform.app.InstrumentationRegistry

class RulesLanguagePickerDeviceTest {
    private var previousAutoAdd: Boolean? = null
    @Before fun disableLiveWrites() {
        val p = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("rules_scanner", 0)
        previousAutoAdd = if (p.contains("auto_unique_art")) p.getBoolean("auto_unique_art", true) else null
        p.edit().putBoolean("auto_unique_art", false).commit()
    }
    @After fun restorePreference() {
        val e = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("rules_scanner", 0).edit()
        previousAutoAdd?.let { e.putBoolean("auto_unique_art", it) } ?: e.remove("auto_unique_art")
        e.commit()
    }
    @Test fun portugueseIsVisibleSelectedAndPassedToConfirmedPrintingWithoutStorageWrites() {
        var selected: Pair<CardEditionOption, String>? = null
        lateinit var review: RulesScanReview
        ActivityScenario.launch(RulesScanActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = HashScanAnalysis.Result(null, emptyList(), listOf("Espírito Flutuante"), listOf("Flickering Spirit"),
                    null, null, null, null, "pt", emptyList(), 0, 0, 0, 0, 0, null,
                    titleLanguage = TitleLanguageIndex.Evidence(listOf(TitleLanguageIndex.Match("Espírito Flutuante", "Flickering Spirit", "Espírito Flutuante", "pt"))))
                val option = CardEditionOption("one", "Flickering Spirit", "Flickering Spirit", "TSP", "Time Spiral", "17", "2006",
                    "common", "foil", true, "", "", "", null, null, null, null)
                val container = LinearLayout(activity)
                review = RulesScanReview(activity, result, container) { card, language -> selected = card to language }
                review.show()
                assertTrue((container.getChildAt(0) as TextView).text.toString().contains("PT"))
                val method = RulesScanReview::class.java.getDeclaredMethod("showLanguages", CardEditionOption::class.java,
                    List::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                val dialogsField = RulesScanReview::class.java.getDeclaredField("dialogs").apply { isAccessible = true }
                fun last() = (dialogsField.get(review) as List<*>).last() as AlertDialog
                method.invoke(review, option, listOf(CardImageVariant("en", "Flickering Spirit", "en-image"),
                    CardImageVariant("pt", "Espírito Flutuante", "pt-image")), false)
                assertEquals(2, last().listView.adapter.count)
                assertEquals(1, last().listView.checkedItemPosition)
                last().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertNull(selected)
                last().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals("pt", selected!!.second)
                assertEquals("one", selected!!.first.printingUuid)
                assertEquals("foil", selected!!.first.finish)
                assertEquals("pt-image", selected!!.first.imageUrl)
                review.close()
            }
        }
    }
}
