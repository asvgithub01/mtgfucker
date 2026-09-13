package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Guides the type-line through the centre while the set symbol sits in the square target. */
class EditionSymbolGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(125, 0, 0, 0) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 215, 92)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
    }
    private val symbolFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 214, 255, 127)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bandWidth = width * .90f
        val bandHeight = width * .22f
        val left = (width - bandWidth) / 2f
        val top = (height - bandHeight) / 2f
        val band = RectF(left, top, left + bandWidth, top + bandHeight)
        canvas.drawRect(0f, 0f, width.toFloat(), band.top, shade)
        canvas.drawRect(0f, band.bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, band.top, band.left, band.bottom, shade)
        canvas.drawRect(band.right, band.top, width.toFloat(), band.bottom, shade)
        val radius = resources.displayMetrics.density * 12f
        canvas.drawRoundRect(band, radius, radius, line)

        val symbolSize = width * .22f
        val symbol = RectF(
            (width - symbolSize) / 2f,
            (height - symbolSize) / 2f,
            (width + symbolSize) / 2f,
            (height + symbolSize) / 2f
        )
        canvas.drawRoundRect(symbol, radius / 2f, radius / 2f, symbolFill)
        canvas.drawRoundRect(symbol, radius / 2f, radius / 2f, line)
    }
}
