package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CardScanGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 215, 92)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
    }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(112, 0, 0, 0) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = resources.displayMetrics.scaledDensity * 15f
    }
    private var message: String = ""

    fun setMessage(value: String) {
        message = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frameHeight = height * .72f
        val frameWidth = frameHeight * 63f / 88f
        val frame = RectF(
            (width - frameWidth) / 2f,
            (height - frameHeight) / 2f,
            (width + frameWidth) / 2f,
            (height + frameHeight) / 2f
        )
        canvas.drawRect(0f, 0f, width.toFloat(), frame.top, shadePaint)
        canvas.drawRect(0f, frame.bottom, width.toFloat(), height.toFloat(), shadePaint)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, shadePaint)
        canvas.drawRect(frame.right, frame.top, width.toFloat(), frame.bottom, shadePaint)
        val radius = resources.displayMetrics.density * 15f
        canvas.drawRoundRect(frame, radius, radius, framePaint)
        if (message.isNotBlank()) {
            val baseline = (frame.bottom + resources.displayMetrics.density * 26f)
                .coerceAtMost(height - resources.displayMetrics.density * 12f)
            canvas.drawText(message, width / 2f, baseline, textPaint)
        }
    }
}
