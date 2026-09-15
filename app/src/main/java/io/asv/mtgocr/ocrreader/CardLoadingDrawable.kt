package io.asv.mtgocr.ocrreader

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock

/** Indeterminate image placeholder; scheduling belongs to the ImageView drawable callback. */
class CardLoadingDrawable : Drawable(), Runnable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 215, 92)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    override fun getIntrinsicWidth() = 160
    override fun getIntrinsicHeight() = 224
    override fun draw(canvas: Canvas) {
        if (!isVisible) return
        val radius = minOf(bounds.width(), bounds.height()) * .15f
        paint.strokeWidth = (radius * .16f).coerceAtLeast(1f)
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius),
            (SystemClock.uptimeMillis() % 1000L) * .36f, 270f, false, paint)
        // ImageView removes this callback when replaced/detached; no Activity or animator leak.
        scheduleSelf(this, SystemClock.uptimeMillis() + 16L)
    }
    override fun run() = invalidateSelf()
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
