package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Fit-center image with bounded pinch/pan and a horizontal page swipe at the base zoom.
 * A pinch owns the complete pointer sequence so lifting one finger cannot become a huge pan.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RoundedCardImageView(context, attrs) {
    var onSwipe: ((direction: Int) -> Unit)? = null
    /** Signed horizontal drag fraction: negative reveals the next page, positive the previous. */
    var onPageDrag: ((fraction: Float) -> Unit)? = null
    var onPageDragEnd: ((fraction: Float, velocityX: Float) -> Unit)? = null
    var onPageDragCancel: (() -> Unit)? = null

    private val zoomMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumSwipeDistance = 72f * resources.displayMetrics.density
    private var userScale = 1f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var gestureHadMultiplePointers = false
    private var pageDragActive = false
    private var pageDragFraction = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                gestureHadMultiplePointers = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val target = (userScale * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                val factor = target / userScale
                if (!factor.isFinite() || abs(factor - 1f) < .0001f) return true
                userScale = target
                zoomMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                constrainTranslation()
                imageMatrix = zoomMatrix
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (userScale <= RESET_ZOOM_THRESHOLD) {
                    resetZoom()
                } else {
                    constrainTranslation()
                    imageMatrix = zoomMatrix
                }
                // A remaining finger gets a fresh origin instead of one large post-pinch jump.
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { resetZoom() }
    }

    fun isZoomed(): Boolean = userScale > RESET_ZOOM_THRESHOLD

    fun resetZoom() {
        val source = drawable ?: return
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0 ||
            source.intrinsicWidth <= 0 || source.intrinsicHeight <= 0
        ) return

        val fit = minOf(
            contentWidth.toFloat() / source.intrinsicWidth,
            contentHeight.toFloat() / source.intrinsicHeight
        )
        val dx = paddingLeft + (contentWidth - source.intrinsicWidth * fit) / 2f
        val dy = paddingTop + (contentHeight - source.intrinsicHeight * fit) / 2f
        zoomMatrix.reset()
        zoomMatrix.postScale(fit, fit)
        zoomMatrix.postTranslate(dx, dy)
        userScale = MIN_ZOOM
        activePointerId = MotionEvent.INVALID_POINTER_ID
        imageMatrix = zoomMatrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastX = event.x
                lastY = event.y
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                gestureHadMultiplePointers = false
                pageDragActive = false
                pageDragFraction = 0f
                parent?.requestDisallowInterceptTouchEvent(isZoomed())
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                gestureHadMultiplePointers = true
                if (pageDragActive) {
                    pageDragActive = false
                    pageDragFraction = 0f
                    onPageDragCancel?.invoke()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && !isZoomed() && !gestureHadMultiplePointers && onPageDrag != null) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val horizontalIntent = abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.15f
                    if (pageDragActive || horizontalIntent) {
                        pageDragActive = true
                        val pageWidth = width.coerceAtLeast(1) * PAGE_TURN_DISTANCE
                        pageDragFraction = (dx / pageWidth).coerceIn(-1f, 1f)
                        onPageDrag?.invoke(pageDragFraction)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                } else if (!scaleDetector.isInProgress && isZoomed()) {
                    var pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex < 0) {
                        pointerIndex = 0
                        activePointerId = event.getPointerId(pointerIndex)
                        lastX = event.getX(pointerIndex)
                        lastY = event.getY(pointerIndex)
                    } else {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        val dx = x - lastX
                        val dy = y - lastY
                        if (abs(dx) > touchSlop / 4f || abs(dy) > touchSlop / 4f) {
                            zoomMatrix.postTranslate(dx, dy)
                            constrainTranslation()
                            imageMatrix = zoomMatrix
                        }
                        lastX = x
                        lastY = y
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                gestureHadMultiplePointers = true
                val liftedIndex = event.actionIndex
                val liftedId = event.getPointerId(liftedIndex)
                if (liftedId == activePointerId) {
                    val replacement = if (liftedIndex == 0) 1 else 0
                    if (replacement < event.pointerCount) {
                        activePointerId = event.getPointerId(replacement)
                        lastX = event.getX(replacement)
                        lastY = event.getY(replacement)
                    } else {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (pageDragActive) {
                    val elapsed = (event.eventTime - downTime).coerceAtLeast(1L)
                    val velocityX = (event.x - downX) / elapsed
                    onPageDragEnd?.invoke(pageDragFraction, velocityX)
                } else if (!gestureHadMultiplePointers && !isZoomed()) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val elapsed = (event.eventTime - downTime).coerceAtLeast(1L)
                    val horizontal = abs(dx) > minimumSwipeDistance && abs(dx) > abs(dy) * 1.35f
                    val intentional = abs(dx) / elapsed > MIN_SWIPE_VELOCITY
                    if (horizontal && intentional) {
                        onSwipe?.invoke(if (dx < 0f) 1 else -1)
                    }
                }
                activePointerId = MotionEvent.INVALID_POINTER_ID
                pageDragActive = false
                pageDragFraction = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                if (pageDragActive) onPageDragCancel?.invoke()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                pageDragActive = false
                pageDragFraction = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun constrainTranslation() {
        val source = drawable ?: return
        val contentWidth = (width - paddingLeft - paddingRight).toFloat()
        val contentHeight = (height - paddingTop - paddingBottom).toFloat()
        if (contentWidth <= 0f || contentHeight <= 0f) return

        zoomMatrix.getValues(matrixValues)
        val scaledWidth = source.intrinsicWidth * matrixValues[Matrix.MSCALE_X]
        val scaledHeight = source.intrinsicHeight * matrixValues[Matrix.MSCALE_Y]
        val minX = paddingLeft + contentWidth - scaledWidth
        val maxX = paddingLeft.toFloat()
        val minY = paddingTop + contentHeight - scaledHeight
        val maxY = paddingTop.toFloat()

        matrixValues[Matrix.MTRANS_X] = if (scaledWidth <= contentWidth) {
            paddingLeft + (contentWidth - scaledWidth) / 2f
        } else {
            matrixValues[Matrix.MTRANS_X].coerceIn(minX, maxX)
        }
        matrixValues[Matrix.MTRANS_Y] = if (scaledHeight <= contentHeight) {
            paddingTop + (contentHeight - scaledHeight) / 2f
        } else {
            matrixValues[Matrix.MTRANS_Y].coerceIn(minY, maxY)
        }
        zoomMatrix.setValues(matrixValues)
    }

    private companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
        const val RESET_ZOOM_THRESHOLD = 1.015f
        const val MIN_SWIPE_VELOCITY = .18f
        const val PAGE_TURN_DISTANCE = .94f
    }
}
