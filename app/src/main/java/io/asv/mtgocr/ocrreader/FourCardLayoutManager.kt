package io.asv.mtgocr.ocrreader

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Two rows, two visible columns; each cell follows the actual viewport on resize. */
class FourCardLayoutManager(context: Context) : GridLayoutManager(context, 2, HORIZONTAL, false) {
    override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
        lp.width = ((width - paddingLeft - paddingRight) / 2 - lp.leftMargin - lp.rightMargin).coerceAtLeast(1)
        lp.height = ((height - paddingTop - paddingBottom) / 2 - lp.topMargin - lp.bottomMargin).coerceAtLeast(1)
        return true
    }
}
