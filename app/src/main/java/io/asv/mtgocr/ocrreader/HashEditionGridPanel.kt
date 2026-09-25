package io.asv.mtgocr.ocrreader

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** One-shot, scrollable set chooser. A missing SVG never removes the textual choice. */
internal class HashEditionGridPanel(
    context: Context,
    sets: List<ArtPrintingIndex.SetInfo>,
    private val selected: (String) -> Unit,
    loadSymbol: (String, ImageView) -> Unit = { code, view -> SetSymbolLoader.display(context, code, view) }
) : ScrollView(context) {
    val tiles = linkedMapOf<String, View>()
    private var consumed = false
    init {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val grid = GridLayout(context).apply { columnCount = 3; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        sets.distinctBy { it.code }.forEachIndexed { i, set ->
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(112)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isClickable = true; isFocusable = true
                contentDescription = "${set.name} (${set.code})"
                val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                try { background = attributes.getDrawable(0) } finally { attributes.recycle() }
                addView(ImageView(context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    loadSymbol(set.code, this)
                }, LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(TextView(context).apply { text = set.code; gravity = Gravity.CENTER; textSize = 16f })
                addView(TextView(context).apply { text = set.name; gravity = Gravity.CENTER; textSize = 12f })
                setOnClickListener {
                    if (!consumed) {
                        consumed = true
                        tiles.values.forEach { it.isEnabled = false }
                        selected(set.code)
                    }
                }
            }
            tiles[set.code] = tile
            grid.addView(tile, GridLayout.LayoutParams(
                GridLayout.spec(i / 3), GridLayout.spec(i % 3, 1f)
            ).apply { width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT })
        }
    }
}
