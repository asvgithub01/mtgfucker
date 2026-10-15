package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test

/** Binding only: no synthetic entries are persisted to the user's collection. */
class LibraryPriceLanguageTest {
    @Test fun recycledRowsShowPhysicalLanguageAndUnknownAcrossLayouts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<OcrCaptureActivity>(Intent(context, OcrCaptureActivity::class.java)
            .putExtra(App.INTENT_PERSISTOR_MODE, "0")).use { scenario ->
            scenario.onActivity { activity ->
                val card = CardInfo("Language test", "", "", "", "1")
                for (mode in listOf(CollectionViewMode.CARD_GRID, CollectionViewMode.ARTWORK_WIDE, CollectionViewMode.ARTWORK_GRID)) {
                    val adapter = MyAdapter(listOf(card), activity, mode)
                    val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                    for ((language, suffix) in listOf("Spanish" to "🇪🇸 ES", "pt" to "🇵🇹 PT", "" to "?", "unknown" to "?", "und" to "?")) {
                        card.languageCode = language
                        adapter.onBindViewHolder(holder, 0)
                        assertTrue(holder.mtxtPrice.text.toString().endsWith("  $suffix"))
                    }
                }
            }
        }
    }
}
