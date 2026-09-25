package io.asv.mtgocr.ocrreader

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Read quality, not ground truth. Does not change recognition or authorize collection writes. */
internal object RulesScanEvidenceTable {
    fun addTo(container: LinearLayout, result: HashScanAnalysis.Result) {
        val context = container.context
        val printing = result.structuredPrinting
        val historical = printing.historical
        val decision = RulesScanReport.decision(result)
        fun row(label: String, value: String, status: String, header: Boolean = false) {
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setBackgroundColor(if (header) 0xFF263238.toInt() else 0x66263238)
            }
            listOf(label to .29f, value to .46f, status to .25f).forEach { (text, weight) ->
                line.addView(TextView(context).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    textSize = if (header) 13f else 12f
                    if (header) setTypeface(typeface, Typeface.BOLD)
                    val padding = (5 * resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                    setTextIsSelectable(!header)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight))
            }
            container.addView(line, LinearLayout.LayoutParams(-1, -2))
        }
        fun field(label: Int, values: List<String>, state: ScanReadState, statusOverride: Int? = null) {
            val status = statusOverride ?: when (state) {
                ScanReadState.READ -> R.string.scan_debug_table_read
                ScanReadState.CONFLICT -> R.string.scan_debug_table_conflict
                ScanReadState.PARTIAL -> R.string.scan_debug_table_partial
                else -> R.string.scan_debug_table_unreadable
            }
            row(context.getString(label), values.filter { it.isNotBlank() }.distinct().joinToString(" / ").ifBlank { "—" }, context.getString(status))
        }
        row(context.getString(R.string.scan_debug_table_data), context.getString(R.string.scan_debug_table_value),
            context.getString(R.string.scan_debug_table_state), true)
        val names = result.names.distinct()
        field(R.string.scan_debug_table_name, names.ifEmpty { result.rawTitle }, when {
            names.isEmpty() -> if (result.rawTitle.isEmpty()) ScanReadState.UNREADABLE else ScanReadState.PARTIAL
            names.size > 1 || decision.status == RulesScanPolicy.Status.CONFLICT -> ScanReadState.CONFLICT
            else -> ScanReadState.READ
        }, if (names.isEmpty() && result.rawTitle.isNotEmpty()) R.string.scan_debug_table_unresolved else null)
        val art = result.rows.firstOrNull()?.candidate?.hit
        val artReason = RulesArtworkEvidence.reason(result.rows.map { row ->
            RulesAutoAddPolicy.Artwork(row.candidate.hit.illustrationId, row.candidate.hit.name,
                row.candidate.hit.phashDistance, row.candidate.hit.dhashDistance, row.variants)
        }, names)
        val artStatus = when (artReason) {
            "STRONG_ART" -> R.string.scan_debug_table_art_strong
            "WEAK_ART" -> R.string.scan_debug_table_art_weak
            "ART_MARGIN" -> R.string.scan_debug_table_art_margin
            "OCR_CONFLICT" -> R.string.scan_debug_table_conflict
            else -> R.string.scan_debug_table_unreadable
        }
        field(R.string.scan_debug_table_art, listOfNotNull(art?.let {
            "${it.name} (p=${it.phashDistance}, d=${it.dhashDistance})"
        }), ScanReadState.PARTIAL, artStatus)
        field(R.string.scan_debug_table_year, historical.years.values, historical.years.state)
        field(R.string.scan_debug_table_artist, historical.artists.values, historical.artists.state)
        val artistCorrections = historical.artists.observations.filter { it.originalValue != null }
            .map { "${it.originalValue} → ${it.value}" }.distinct()
        if (artistCorrections.isNotEmpty()) row(context.getString(R.string.scan_debug_artist_normalized),
            artistCorrections.joinToString(" / "), context.getString(R.string.scan_debug_artist_dictionary))
        field(R.string.scan_debug_table_historical_number, historical.collectorNumbers.values, historical.collectorNumbers.state)
        field(R.string.scan_debug_table_printed_total, historical.printedTotals.values, historical.printedTotals.state)
        val numbers = printing.candidates.mapNotNull { it.number }.distinct()
        field(R.string.scan_debug_table_modern_number, numbers, when {
            numbers.isEmpty() -> ScanReadState.UNREADABLE
            numbers.size > 1 -> ScanReadState.CONFLICT
            else -> ScanReadState.READ
        })
        val sets = printing.candidates.map { it.setCode }.distinct()
        field(R.string.scan_debug_table_set, sets, when {
            sets.isEmpty() -> ScanReadState.UNREADABLE
            sets.size > 1 -> ScanReadState.CONFLICT
            else -> ScanReadState.READ
        })
        field(R.string.scan_debug_table_title_language, result.titleLanguage.matches.map { "${it.language}: ${it.printed}" }.distinct(), when {
            result.titleLanguage.conflict -> ScanReadState.CONFLICT
            result.titleLanguage.candidates.size == 1 -> ScanReadState.READ
            result.titleLanguage.candidates.size > 1 -> ScanReadState.PARTIAL
            else -> ScanReadState.UNREADABLE
        })
        field(R.string.scan_debug_table_language, listOf(decision.language), decision.languageState)
        container.addView(TextView(context).apply {
            setTextColor(Color.LTGRAY)
            textSize = 11f
            setText(R.string.scan_debug_table_note)
        })
    }
}
