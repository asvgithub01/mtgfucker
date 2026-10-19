package io.asv.mtgocr.ocrreader

import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

internal object PostprocessEvidencePanel {
    fun bind(activity: AppCompatActivity, item: String, onCardChanged: () -> Unit): ScanReviewPanel? {
        val section = activity.findViewById<View>(R.id.postprocessSection)
        section.visibility = View.GONE
        if (!PremiumAccess.isEnabled(activity) || item.isBlank()) return null
        section.visibility = View.VISIBLE
        val body = activity.findViewById<LinearLayout>(R.id.postprocessBody)
        return ScanReviewPanel(activity, body, item, onCardChanged).also { it.create(null) }
    }
}
