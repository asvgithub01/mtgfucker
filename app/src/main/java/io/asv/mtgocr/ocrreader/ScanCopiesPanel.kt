package io.asv.mtgocr.ocrreader

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Shared real view, independently testable without opening a camera or writing a collection. */
internal class ScanCopiesPanel(context: Context, card: String, set: String, private val saved: Int) :
    LinearLayout(context) {
    val quantity = EditText(context)
    val minus = Button(context)
    val plus = Button(context)
    val edition = Button(context)

    fun showEdition(label: String) {
        edition.text = "${context.getString(R.string.change_edition)}: $label"
    }

    init {
        orientation = VERTICAL
        val pad = (20 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)
        addView(TextView(context).apply { text = card; textSize = 20f })
        showEdition(set)
        addView(edition)
        addView(TextView(context).apply { setText(R.string.scan_copies_edition_help) })
        addView(TextView(context).apply {
            text = context.getString(R.string.scan_copies_help, saved)
        })
        addView(TextView(context).apply { text = context.getString(R.string.scan_copies_number) })
        quantity.apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            setSingleLine(true)
            setText(saved.toString())
            contentDescription = context.getString(R.string.scan_copies_number)
        }
        minus.apply {
            text = "−"
            contentDescription = context.getString(R.string.decrease_quantity)
            setOnClickListener { adjust(-1) }
        }
        plus.apply {
            text = "+"
            contentDescription = context.getString(R.string.increase_quantity)
            setOnClickListener { adjust(1) }
        }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(minus, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(quantity, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.4f))
            addView(plus, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        })
    }

    private fun adjust(delta: Int) {
        val current = quantity.text.toString().toIntOrNull() ?: saved
        quantity.setText((current.toLong() + delta).coerceIn(saved.toLong(), RepeatedScanCopies.MAX_COPIES.toLong()).toString())
        quantity.error = null
    }
}
