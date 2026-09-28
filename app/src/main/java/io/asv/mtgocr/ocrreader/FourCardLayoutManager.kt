package io.asv.mtgocr.ocrreader

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Two rows, two visible columns; each cell follows the actual viewport on resize. */
class FourCardLayoutManager @JvmOverloads constructor(context: Context, private val columns: Int = 2, rows: Int = 2) : GridLayoutManager(context, rows, HORIZONTAL, false) {
    override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
        lp.width = ((width - paddingLeft - paddingRight) / columns - lp.leftMargin - lp.rightMargin).coerceAtLeast(1)
        lp.height = ((height - paddingTop - paddingBottom) / spanCount - lp.topMargin - lp.bottomMargin).coerceAtLeast(1)
        return true
    }
}
