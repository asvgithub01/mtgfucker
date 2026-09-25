package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.CardRepository

object EditionPicker {
    /** Always available from session rows, independent of the scanner's opt-in popup flag. */
    @JvmStatic
    fun showGrid(activity: Activity, cardName: String, preferredFinish: String, onSelected: (CardEditionOption) -> Unit): AlertDialog {
        lateinit var dialog: AlertDialog
        val content = EditionGridContent(activity, preferredFinish, selected = { option ->
            if (dialog.isShowing && !activity.isFinishing && !activity.isDestroyed) {
                dialog.dismiss()
                onSelected(option)
            }
        })
        dialog = AlertDialog.Builder(activity).setTitle(R.string.editions).setView(content)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.show()
        var hasOptions = false
        val task = CardRepository.get(activity).loadCard(cardName, deliverEditionsBeforePrices = true) { options, error ->
            if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return@loadCard
            if (error != null) {
                if (!hasOptions) content.showError()
            } else {
                hasOptions = options.isNotEmpty()
                content.showOptions(options)
            }
        }
        dialog.setOnDismissListener { task.cancel(true) }
        return dialog
    }

    @JvmStatic
    fun show(activity: Activity, cardName: String, preferredFinish: String, onSelected: (CardEditionOption) -> Unit): AlertDialog {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val search = AutoCompleteTextView(activity).apply {
            setHint(R.string.edition_search_hint)
            threshold = 1
            isSingleLine = true
        }
        val status = TextView(activity).apply { setText(R.string.loading_editions) }
        val list = ListView(activity)
        content.addView(search)
        content.addView(status)
        content.addView(list, LinearLayout.LayoutParams(-1, (360 * activity.resources.displayMetrics.density).toInt()))
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.editions).setView(content)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.show()
        var hasOptions = false
        val task = CardRepository.get(activity).loadCard(cardName, deliverEditionsBeforePrices = true) { options, error ->
            if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return@loadCard
            if (error != null) {
                if (hasOptions) return@loadCard
                status.setText(R.string.editions_error)
                return@loadCard
            }
            hasOptions = options.isNotEmpty()
            status.setText(R.string.edition_search_no_results)
            list.emptyView = status
            val ordered = options.sortedBy { if (it.finish.equals(preferredFinish, ignoreCase = true)) 0 else 1 }
            val entries = ordered.map { EditionSearchAdapter.Entry(it.setCode, it.setName, "#${it.collectorNumber} · ${it.finish}", it.imageUrl, "${it.printingUuid}:${it.finish}") }
            val rows = EditionSearchAdapter(activity, entries)
            list.adapter = rows
            search.setAdapter(EditionSearchAdapter(activity, entries.distinctBy { it.code }.map { EditionSearchAdapter.Entry(it.code, it.name) }))
            search.doAfterTextChanged { rows.filter.filter(it?.toString()) }
            rows.filter.filter(search.text.toString())
            list.setOnItemClickListener { _, _, position, _ ->
                if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return@setOnItemClickListener
                val entry = rows.getItem(position)
                val index = entries.indexOf(entry)
                dialog.dismiss()
                if (index >= 0) onSelected(ordered[index])
            }
        }
        dialog.setOnDismissListener { task.cancel(true) }
        return dialog
    }
}
