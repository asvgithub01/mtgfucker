package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Guides the complete card and makes the border sample positions visible before capture. */
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
    private val sampleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 214, 255, 127)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var cardHeight = height * .72f
        var cardWidth = cardHeight * (63f / 88f)
        if (cardWidth > width * .90f) {
            cardWidth = width * .90f
            cardHeight = cardWidth * (88f / 63f)
        }
        val left = (width - cardWidth) / 2f
        val top = (height - cardHeight) / 2f
        val card = RectF(left, top, left + cardWidth, top + cardHeight)
        canvas.drawRect(0f, 0f, width.toFloat(), card.top, shade)
        canvas.drawRect(0f, card.bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, card.top, card.left, card.bottom, shade)
        canvas.drawRect(card.right, card.top, width.toFloat(), card.bottom, shade)
        val radius = resources.displayMetrics.density * 12f
        canvas.drawRoundRect(card, radius, radius, line)

        val symbolSize = cardWidth * .15f
        val symbol = RectF(
            card.left + cardWidth * .81f,
            card.top + cardHeight * .515f,
            card.left + cardWidth * .81f + symbolSize,
            card.top + cardHeight * .515f + symbolSize * .72f
        )
        canvas.drawRoundRect(symbol, radius / 3f, radius / 3f, sampleFill)

        val inset = cardWidth * .027f
        val dotRadius = resources.displayMetrics.density * 4.5f
        val positions = floatArrayOf(.16f, .38f, .62f, .84f)
        for (position in positions) {
            val x = card.left + cardWidth * position
            val y = card.top + cardHeight * position
            canvas.drawCircle(x, card.top + inset, dotRadius, sampleFill)
            canvas.drawCircle(x, card.bottom - inset, dotRadius, sampleFill)
            canvas.drawCircle(card.left + inset, y, dotRadius, sampleFill)
            canvas.drawCircle(card.right - inset, y, dotRadius, sampleFill)
        }
    }
}
