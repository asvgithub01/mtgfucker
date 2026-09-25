package io.asv.mtgocr.ocrreader

import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView

/** Separate from the edition picker: this panel cannot save or select a printing. */
internal object HistoricalPrintingComparisonView {
    fun addTo(container: LinearLayout, result: HashScanAnalysis.Result) {
        val comparison = RulesScanReport.historicalComparison(result)
        if (comparison.state == "NOT_APPLICABLE") return
        val context = container.context
        fun text(value: String, bold: Boolean = false) {
            container.addView(TextView(context).apply {
                this.text = value; setTextColor(Color.WHITE); textSize = 12f
                setTextIsSelectable(true)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                val padding = (6 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            })
        }
        text(context.getString(R.string.scan_debug_historical_compare_title), true)
        if (comparison.candidates.isEmpty()) {
            text(context.getString(R.string.scan_debug_historical_compare_empty, comparison.state))
            return
        }
        fun mark(match: HistoricalPrintingComparison.Match) = when (match) {
            HistoricalPrintingComparison.Match.MATCH -> context.getString(R.string.scan_debug_compare_match)
            HistoricalPrintingComparison.Match.DIFFERENT -> context.getString(R.string.scan_debug_compare_different)
            HistoricalPrintingComparison.Match.OCR_CONFLICT -> context.getString(R.string.scan_debug_table_conflict)
            HistoricalPrintingComparison.Match.UNKNOWN -> context.getString(R.string.scan_debug_compare_unknown)
        }
        text(context.getString(R.string.scan_debug_historical_compare_count, comparison.candidates.size))
        comparison.candidates.take(8).forEach { candidate ->
            text(context.getString(R.string.scan_debug_historical_compare_row, candidate.set, candidate.setName,
                candidate.number.ifBlank { "—" }, mark(candidate.numberMatch), candidate.releaseYear.ifBlank { "—" },
                mark(candidate.releaseYearMatch)) + "\n" + context.getString(R.string.scan_debug_historical_compare_artist,
                candidate.artist.ifBlank { "—" }, mark(candidate.artistMatch)) + "\n" +
                context.getString(R.string.scan_debug_historical_compare_language,
                    candidate.languages.joinToString(" / "), mark(candidate.languageMatch)) + if (candidate.retainedFooterPossible)
                "\n" + context.getString(R.string.scan_debug_historical_compare_retained) else "")
        }
        text(context.getString(R.string.scan_debug_historical_compare_limit))
    }
}
