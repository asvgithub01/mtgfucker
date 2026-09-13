package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Lets the user place four card corners over a frozen photo and correct perspective afterwards. */
class CardCropAdjustView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var photo: Bitmap? = null
    private val corners = Array(4) { PointF() } // top-left, top-right, bottom-right, bottom-left
    private val imageRect = RectF()
    private var imageScale = 1f
    private var activeHandle = NONE
    private var lastImageX = 0f
    private var lastImageY = 0f

    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 215, 92)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(214, 255, 127)
        style = Paint.Style.FILL
    }
    private val samplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 214, 255, 127)
        style = Paint.Style.FILL
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 214, 255, 127)
        style = Paint.Style.FILL
    }

    fun setPhoto(bitmap: Bitmap, suggestedBounds: Rect) {
        clearPhoto()
        photo = bitmap
        val left = suggestedBounds.left.toFloat().coerceIn(0f, bitmap.width - 2f)
        val top = suggestedBounds.top.toFloat().coerceIn(0f, bitmap.height - 2f)
        val right = suggestedBounds.right.toFloat().coerceIn(left + 2f, bitmap.width.toFloat())
        val bottom = suggestedBounds.bottom.toFloat().coerceIn(top + 2f, bitmap.height.toFloat())
        corners[0].set(left, top)
        corners[1].set(right, top)
        corners[2].set(right, bottom)
        corners[3].set(left, bottom)
        invalidate()
    }

    fun clearPhoto() {
        photo?.takeIf { !it.isRecycled }?.recycle()
        photo = null
        activeHandle = NONE
        invalidate()
    }

    fun extractCardBitmap(width: Int = 630, height: Int = 880): Bitmap? {
        val bitmap = photo ?: return null
        if (!validQuad(corners, bitmap.width.toFloat(), bitmap.height.toFloat())) return null
        val source = floatArrayOf(
            corners[0].x, corners[0].y,
            corners[1].x, corners[1].y,
            corners[2].x, corners[2].y,
            corners[3].x, corners[3].y
        )
        val destination = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat()
        )
        val transform = Matrix()
        if (!transform.setPolyToPoly(source, 0, destination, 0, 4)) return null
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { corrected ->
            Canvas(corrected).apply {
                drawColor(Color.BLACK)
                drawBitmap(bitmap, transform, photoPaint)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = photo ?: return
        calculateImageRect(bitmap)
        canvas.drawBitmap(bitmap, null, imageRect, photoPaint)

        val quad = quadPath()
        val shade = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(imageRect, Path.Direction.CW)
            addPath(quad)
        }
        canvas.drawPath(shade, shadePaint)
        canvas.drawPath(quad, linePaint)

        drawEvidenceGuides(canvas)
        val radius = resources.displayMetrics.density * 12f
        for (corner in corners) {
            val viewPoint = toView(corner.x, corner.y)
            canvas.drawCircle(viewPoint.x, viewPoint.y, radius, handlePaint)
            canvas.drawCircle(viewPoint.x, viewPoint.y, radius, linePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bitmap = photo ?: return false
        val point = toImage(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = nearestHandle(event.x, event.y)
                if (activeHandle == NONE && pointInQuad(point.x, point.y)) activeHandle = MOVE
                if (activeHandle == NONE) return false
                lastImageX = point.x
                lastImageY = point.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = point.x - lastImageX
                val dy = point.y - lastImageY
                if (activeHandle == MOVE) {
                    moveQuad(dx, dy, bitmap)
                } else if (activeHandle in corners.indices) {
                    moveCorner(activeHandle, point.x, point.y, bitmap)
                }
                lastImageX = point.x
                lastImageY = point.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun calculateImageRect(bitmap: Bitmap) {
        imageScale = min(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())
        val renderedWidth = bitmap.width * imageScale
        val renderedHeight = bitmap.height * imageScale
        imageRect.set(
            (width - renderedWidth) / 2f,
            (height - renderedHeight) / 2f,
            (width + renderedWidth) / 2f,
            (height + renderedHeight) / 2f
        )
    }

    private fun quadPath(): Path = Path().apply {
        val first = toView(corners[0].x, corners[0].y)
        moveTo(first.x, first.y)
        for (index in 1..3) {
            val point = toView(corners[index].x, corners[index].y)
            lineTo(point.x, point.y)
        }
        close()
    }

    private fun drawEvidenceGuides(canvas: Canvas) {
        val positions = floatArrayOf(.16f, .38f, .62f, .84f)
        val radius = resources.displayMetrics.density * 4f
        for (position in positions) {
            drawSample(canvas, bilinear(position, .027f), radius)
            drawSample(canvas, bilinear(position, .973f), radius)
            drawSample(canvas, bilinear(.027f, position), radius)
            drawSample(canvas, bilinear(.973f, position), radius)
        }
        val symbol = arrayOf(
            bilinear(.81f, .515f),
            bilinear(.96f, .515f),
            bilinear(.96f, .61f),
            bilinear(.81f, .61f)
        )
        val symbolPath = Path().apply {
            val first = toView(symbol[0].x, symbol[0].y)
            moveTo(first.x, first.y)
            for (index in 1..3) {
                val point = toView(symbol[index].x, symbol[index].y)
                lineTo(point.x, point.y)
            }
            close()
        }
        canvas.drawPath(symbolPath, symbolPaint)
        canvas.drawPath(symbolPath, linePaint)
    }

    private fun drawSample(canvas: Canvas, imagePoint: PointF, radius: Float) {
        val point = toView(imagePoint.x, imagePoint.y)
        canvas.drawCircle(point.x, point.y, radius, samplePaint)
    }

    private fun bilinear(horizontal: Float, vertical: Float): PointF {
        val topX = lerp(corners[0].x, corners[1].x, horizontal)
        val topY = lerp(corners[0].y, corners[1].y, horizontal)
        val bottomX = lerp(corners[3].x, corners[2].x, horizontal)
        val bottomY = lerp(corners[3].y, corners[2].y, horizontal)
        return PointF(lerp(topX, bottomX, vertical), lerp(topY, bottomY, vertical))
    }

    private fun nearestHandle(viewX: Float, viewY: Float): Int {
        val threshold = resources.displayMetrics.density * 42f
        var best = NONE
        var bestDistance = threshold
        for (index in corners.indices) {
            val point = toView(corners[index].x, corners[index].y)
            val distance = hypot(viewX - point.x, viewY - point.y)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    private fun moveQuad(dx: Float, dy: Float, bitmap: Bitmap) {
        val minX = corners.minOf { it.x }
        val maxX = corners.maxOf { it.x }
        val minY = corners.minOf { it.y }
        val maxY = corners.maxOf { it.y }
        val safeDx = dx.coerceIn(-minX, bitmap.width - maxX)
        val safeDy = dy.coerceIn(-minY, bitmap.height - maxY)
        corners.forEach { it.offset(safeDx, safeDy) }
    }

    private fun moveCorner(index: Int, x: Float, y: Float, bitmap: Bitmap) {
        val old = PointF(corners[index].x, corners[index].y)
        corners[index].set(
            x.coerceIn(0f, bitmap.width.toFloat()),
            y.coerceIn(0f, bitmap.height.toFloat())
        )
        if (!validQuad(corners, bitmap.width.toFloat(), bitmap.height.toFloat())) {
            corners[index].set(old.x, old.y)
        }
    }

    private fun pointInQuad(x: Float, y: Float): Boolean {
        var inside = false
        var previous = corners.lastIndex
        for (index in corners.indices) {
            val current = corners[index]
            val prior = corners[previous]
            if ((current.y > y) != (prior.y > y) &&
                x < (prior.x - current.x) * (y - current.y) /
                    (prior.y - current.y).takeIf { abs(it) > .0001f }!! + current.x
            ) inside = !inside
            previous = index
        }
        return inside
    }

    private fun toView(x: Float, y: Float): PointF = PointF(
        imageRect.left + x * imageScale,
        imageRect.top + y * imageScale
    )

    private fun toImage(x: Float, y: Float): PointF = PointF(
        ((x - imageRect.left) / imageScale.coerceAtLeast(.0001f)),
        ((y - imageRect.top) / imageScale.coerceAtLeast(.0001f))
    )

    companion object {
        private const val NONE = -1
        private const val MOVE = 4

        internal fun validQuad(points: Array<PointF>, width: Float, height: Float): Boolean {
            if (points.size != 4) return false
            val minimumEdge = min(width, height) * .15f
            for (index in points.indices) {
                val next = points[(index + 1) % points.size]
                if (hypot(points[index].x - next.x, points[index].y - next.y) < minimumEdge) return false
            }
            var sign = 0
            var area = 0f
            for (index in points.indices) {
                val a = points[index]
                val b = points[(index + 1) % points.size]
                val c = points[(index + 2) % points.size]
                val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                if (abs(cross) < .001f) return false
                val currentSign = if (cross > 0) 1 else -1
                if (sign != 0 && currentSign != sign) return false
                sign = currentSign
                area += a.x * b.y - b.x * a.y
            }
            return abs(area) * .5f >= width * height * .025f
        }

        private fun lerp(start: Float, end: Float, amount: Float): Float =
            start + (end - start) * amount
    }
}
