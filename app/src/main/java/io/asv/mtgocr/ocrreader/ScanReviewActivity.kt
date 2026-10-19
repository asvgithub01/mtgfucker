package io.asv.mtgocr.ocrreader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Full postprocessing screen; card details reuse the same panel and row actions. */
class ScanReviewActivity : AppCompatActivity() {
    companion object { const val EXTRA_GROUP = "review_group" }
    private lateinit var panel: ScanReviewPanel
    override fun onCreate(state: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(state)
        panel = ScanReviewPanel(this)
        panel.create(state)
    }
    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        panel.saveInstanceState(state)
    }
}
