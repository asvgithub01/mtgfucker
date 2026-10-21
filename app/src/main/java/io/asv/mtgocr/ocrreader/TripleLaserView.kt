package io.asv.mtgocr.ocrreader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout

/** Decorative only: a small translated drawing, never part of camera frames or recognition. */
class TripleLaserView(context: Context) : FrameLayout(context) {
    private val stripe = Stripe(context)
    private var animator: ValueAnimator? = null
    private var enabledByUser = false
    private var hostActive = false
    internal val isAnimating: Boolean get() = animator?.isRunning == true

    init {
        tag = "triple_laser_overlay"
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        clipChildren = true
        addView(stripe, LayoutParams(LayoutParams.MATCH_PARENT, (28 * resources.displayMetrics.density).toInt()))
        visibility = GONE
    }

    fun setLaserEnabled(enabled: Boolean) {
        enabledByUser = enabled
        visibility = if (enabled) VISIBLE else GONE
        syncAnimation()
    }

    fun setHostActive(active: Boolean) {
        hostActive = active
        syncAnimation()
    }

    private fun syncAnimation() {
        val shouldRun = enabledByUser && hostActive && isAttachedToWindow && isShown &&
            windowVisibility == VISIBLE && height > stripe.height && stripe.height > 0 &&
            (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
        if (!shouldRun) {
            animator?.cancel()
            animator = null
            stripe.visibility = INVISIBLE
            return
        }
        stripe.visibility = VISIBLE
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                stripe.translationY = (it.animatedValue as Float) * (height - stripe.height).coerceAtLeast(0)
            }
            start()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        stripe.translationY = stripe.translationY.coerceIn(0f, (height - stripe.height).coerceAtLeast(0).toFloat())
        syncAnimation()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() {
        animator?.cancel(); animator = null
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) syncAnimation()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) syncAnimation()
    }

    private class Stripe(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val density = resources.displayMetrics.density
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            paint.shader = LinearGradient(0f, 0f, w.coerceAtLeast(1).toFloat(), 0f,
                intArrayOf(Color.TRANSPARENT, 0xFF00F2FE.toInt(), Color.WHITE, 0xFF00F2FE.toInt(), Color.TRANSPARENT),
                floatArrayOf(0f, .25f, .5f, .75f, 1f), Shader.TileMode.CLAMP)
        }
        override fun onDraw(canvas: Canvas) {
            val center = height / 2f
            // Layered translucent bands provide a glow without bitmap assets or software blur.
            for (band in 12 downTo 2) {
                paint.alpha = 8
                canvas.drawRect(0f, center - band * density, width.toFloat(), center + band * density, paint)
            }
            paint.alpha = 255
            canvas.drawRect(0f, center - density, width.toFloat(), center + density, paint)
        }
    }
}
