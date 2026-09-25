package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import java.util.Locale

/** Review is a separate UI state: no catalog row or language is accepted without a tap. */
internal class RulesScanReview(
    private val activity: Activity,
    private val result: HashScanAnalysis.Result,
    private val container: LinearLayout,
    private val selected: (CardEditionOption, String) -> Unit
) {
    private var languageRequest = 0
    private var languageTask: java.util.concurrent.Future<*>? = null
    private var active = true
    private var submitted = false
    private val dialogs = mutableListOf<Dialog>()
    private fun usable() = active && !submitted && !activity.isFinishing && !activity.isDestroyed

    fun show() {
        container.removeAllViews()
        val decision = RulesScanReport.decision(result)
        container.addView(TextView(activity).apply {
            text = if (decision.language.isBlank()) activity.getString(R.string.edition_scan_candidate_language_unknown)
                else activity.getString(R.string.scan_session_language, decision.language.uppercase(Locale.ROOT))
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        })
        RulesScanEvidenceTable.addTo(container, result)
        HistoricalPrintingComparisonView.addTo(container, result)
        // Even weak visual names may be manually explored, but never labelled recognized identities.
        val names = (decision.identities + result.rows.map { it.candidate.hit.name }).distinct().take(8)
        names.forEach { name ->
            container.addView(Button(activity).apply {
                text = activity.getString(R.string.rules_scan_review_name, name)
                setOnClickListener { openCatalog(name) }
            })
        }
        container.addView(Button(activity).apply {
            setText(R.string.rules_scan_search)
            setOnClickListener {
                if (!usable()) return@setOnClickListener
                val name = EditText(activity).apply { isSingleLine = true; setHint(R.string.rules_scan_name_hint) }
                val dialog = AlertDialog.Builder(activity).setTitle(R.string.rules_scan_search).setView(name)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, null).create()
                dialogs += dialog
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (name.text.toString().isNotBlank()) {
                        dialog.dismiss()
                        openCatalog(name.text.toString().trim())
                    }
                }
            }
        })
        container.addView(Button(activity).apply {
            setText(R.string.rules_scan_evidence)
            setOnClickListener {
                if (usable()) dialogs += AlertDialog.Builder(activity).setTitle(R.string.rules_scan_evidence)
                    .setMessage(RulesScanReport.json(result).toString(2))
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        })
    }

    private fun openCatalog(name: String) {
        if (!usable()) return
        // All catalog rows, all finishes; no dependence on the truncated visual shortlist.
        dialogs += EditionPicker.show(activity, name, "") { option -> if (usable()) chooseLanguage(option) }
    }

    private fun chooseLanguage(option: CardEditionOption) {
        if (!usable()) return
        val request = ++languageRequest
        languageTask?.cancel(true)
        val loading = AlertDialog.Builder(activity).setTitle(R.string.rules_scan_language)
            .setMessage(R.string.rules_language_loading)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                languageRequest++; languageTask?.cancel(true)
            }.create()
        loading.setOnCancelListener { languageRequest++; languageTask?.cancel(true) }
        dialogs += loading
        loading.show()
        languageTask = io.asv.mtgocr.ocrreader.data.CardRepository.get(activity)
            .loadImageLanguages(option.setCode, option.collectorNumber) { variants, error ->
                if (!usable() || request != languageRequest) return@loadImageLanguages
                loading.dismiss()
                showLanguages(option, variants, error != null || variants.isEmpty())
            }
    }

    private fun showLanguages(option: CardEditionOption,
        variants: List<io.asv.mtgocr.ocrreader.data.CardImageVariant>, unavailable: Boolean) {
        val codes = RulesLanguageChoices.codes(variants.map { it.languageCode }, unavailable)
        val labels = codes.map { code ->
            val name = when (code) {
                "zhs" -> activity.getString(R.string.language_chinese_simplified)
                "zht" -> activity.getString(R.string.language_chinese_traditional)
                "phyrexian" -> activity.getString(R.string.language_phyrexian)
                else -> Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
            }
            "$name ($code)"
        }
        val observed = RulesScanReport.decision(result).language
        var chosen = codes.indexOf(observed)
        val header = android.widget.TextView(activity).apply {
            text = activity.getString(R.string.rules_language_catalog_status,
                option.setCode, option.collectorNumber, observed.ifBlank { "—" }) +
                if (unavailable) "\n" + activity.getString(R.string.rules_language_unavailable) else ""
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val dialog = AlertDialog.Builder(activity).setCustomTitle(header)
            .setSingleChoiceItems(labels.toTypedArray(), chosen) { _, index -> chosen = index }
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialogs += dialog
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (usable() && chosen in codes.indices) {
                dialog.dismiss()
                val language = codes[chosen]
                val localized = RulesScanReport.localizedOption(result, option, language)
                val reference = variants.singleOrNull { it.languageCode == language }
                confirm(if (reference == null) localized else localized.copy(
                    displayName = reference.printedName, imageUrl = reference.imageUrl), language)
            }
        }
    }

    private fun confirm(option: CardEditionOption, language: String) {
        dialogs += AlertDialog.Builder(activity).setTitle(R.string.rules_scan_confirm)
            .setMessage("${option.displayName}\n${option.setName} (${option.setCode}) #${option.collectorNumber}\n${option.finish} · $language")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (usable()) {
                    submitted = true
                    container.removeAllViews()
                    selected(option, language)
                }
            }.show()
    }

    fun close() {
        active = false
        languageRequest++
        languageTask?.cancel(true)
        dialogs.forEach { it.dismiss() }
        dialogs.clear()
        container.removeAllViews()
    }
}
