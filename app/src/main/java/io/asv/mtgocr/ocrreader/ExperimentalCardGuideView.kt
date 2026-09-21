package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewConfiguration
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/** Draws the live OpenCV quadrilateral and the progress towards automatic capture. */
class ExperimentalCardGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var corners: Array<PointF>? = null
    private var sourceAspect = 3f / 4f
    private var stability = 0f
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(105, 0, 0, 0) }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 215, 92)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2.5f
    }
    private val detected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 4f
        strokeJoin = Paint.Join.ROUND
    }

    fun showDetection(quad: OpenCvDetectedQuad?, progress: Float) {
        corners = quad?.normalizedCorners?.map { PointF(it.x, it.y) }?.toTypedArray()
        sourceAspect = quad?.let { it.sourceWidth / it.sourceHeight.toFloat() } ?: 3f / 4f
        stability = progress.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    private fun expectedCard(): RectF {
        var cardHeight = height * .72f
        var cardWidth = cardHeight * (63f / 88f)
        if (cardWidth > width * .90f) {
            cardWidth = width * .90f
            cardHeight = cardWidth * (88f / 63f)
        }
        return RectF(
            (width - cardWidth) / 2f,
            (height - cardHeight) / 2f,
            (width + cardWidth) / 2f,
            (height + cardHeight) / 2f
        )
    }

    var onCardTap: (() -> Unit)? = null
    private var cardTapStarted = false
    private var tapMoved = false
    private var tapX = 0f
    private var tapY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onCardTap == null) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cardTapStarted = expectedCard().contains(event.x, event.y)
                tapX = event.x
                tapY = event.y
                tapMoved = false
                return cardTapStarted
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - tapX) > touchSlop ||
                    kotlin.math.abs(event.y - tapY) > touchSlop) tapMoved = true
            }
            MotionEvent.ACTION_UP -> {
                val tapped = cardTapStarted && !tapMoved && expectedCard().contains(event.x, event.y)
                cardTapStarted = false
                if (tapped) performClick()
                return tapped
            }
            MotionEvent.ACTION_CANCEL -> cardTapStarted = false
        }
        return cardTapStarted
    }

    override fun performClick(): Boolean {
        super.performClick()
        onCardTap?.invoke()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val expected = expectedCard()
        canvas.drawRect(0f, 0f, width.toFloat(), expected.top, shade)
        canvas.drawRect(0f, expected.bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, expected.top, expected.left, expected.bottom, shade)
        canvas.drawRect(expected.right, expected.top, width.toFloat(), expected.bottom, shade)
        val radius = resources.displayMetrics.density * 12f
        canvas.drawRoundRect(expected, radius, radius, guide)

        val points = corners ?: return
        if (points.size != 4) return
        detected.color = blend(Color.rgb(255, 193, 7), Color.rgb(76, 220, 112), stability)
        val path = Path()
        points.forEachIndexed { index, point ->
            val mapped = mapPoint(point)
            if (index == 0) path.moveTo(mapped.x, mapped.y) else path.lineTo(mapped.x, mapped.y)
        }
        path.close()
        canvas.drawPath(path, detected)
    }

    private fun mapPoint(point: PointF): PointF {
        val virtualHeight = 1_000f
        val virtualWidth = virtualHeight * sourceAspect
        val scale = max(width / virtualWidth, height / virtualHeight)
        val offsetX = (width - virtualWidth * scale) / 2f
        val offsetY = (height - virtualHeight * scale) / 2f
        return PointF(
            offsetX + point.x * virtualWidth * scale,
            offsetY + point.y * virtualHeight * scale
        )
    }

    private fun blend(start: Int, end: Int, amount: Float): Int = Color.rgb(
        (Color.red(start) + (Color.red(end) - Color.red(start)) * amount).toInt(),
        (Color.green(start) + (Color.green(end) - Color.green(start)) * amount).toInt(),
        (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * amount).toInt()
    )
}
