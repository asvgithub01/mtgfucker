package io.asv.mtgocr.ocrreader

import android.content.Context
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import io.asv.mtgocr.ocrreader.data.CardEditionOption

/** Shared manual session chooser, with no dependency on scanner preferences or collection writes. */
internal class EditionGridContent(
    context: Context,
    private val preferredFinish: String,
    private val selected: (CardEditionOption) -> Unit,
    private val loadSymbol: (String, ImageView) -> Unit = { code, view -> SetSymbolLoader.display(context, code, view) }
) : LinearLayout(context) {
    val search = EditText(context).apply { setHint(R.string.edition_search_hint); isSingleLine = true }
    val status = TextView(context).apply { setText(R.string.loading_editions) }
    val body = FrameLayout(context)
    private var options = emptyList<CardEditionOption>()
    private var consumed = false

    init {
        orientation = VERTICAL
        addView(search)
        addView(status)
        addView(body, LayoutParams(-1, (360 * resources.displayMetrics.density).toInt()))
        search.doAfterTextChanged { render() }
    }

    fun showError() { status.visibility = VISIBLE; status.setText(R.string.editions_error) }
    fun showOptions(value: List<CardEditionOption>) { options = value; render() }

    private fun choose(option: CardEditionOption) {
        if (!consumed) { consumed = true; selected(option) }
    }

    private fun render() {
        body.removeAllViews()
        search.visibility = VISIBLE
        val visible = EditionGridPolicy.sets(options).filter { EditionSearch.matches(it.code, it.name, search.text.toString()) }
        status.visibility = if (visible.isEmpty()) VISIBLE else GONE
        status.setText(R.string.edition_search_no_results)
        body.addView(HashEditionGridPanel(context, visible, selected = { code ->
            val choices = EditionGridPolicy.choices(options, code, preferredFinish)
            if (choices.size == 1) choose(choices.single())
            else if (choices.isNotEmpty()) showPrintings(choices)
        }, loadSymbol = loadSymbol))
    }

    private fun showPrintings(choices: List<CardEditionOption>) {
        search.visibility = GONE
        body.removeAllViews()
        val rows = choices.map { EditionSearchAdapter.Entry(it.setCode, it.setName,
            "#${it.collectorNumber} · ${it.finish}", it.imageUrl, "${it.printingUuid}:${it.finish}") }
        body.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(Button(context).apply {
                setText(R.string.experimental_scan_back_to_editions)
                setOnClickListener { render() }
            })
            addView(ListView(context).apply {
                adapter = EditionSearchAdapter(context, rows)
                setOnItemClickListener { _, _, position, _ -> choose(choices[position]) }
            }, LayoutParams(-1, 0, 1f))
        })
    }
}
