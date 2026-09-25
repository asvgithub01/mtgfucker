package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** Clips the image and every badge/overlay as one card-shaped surface. */
class RoundedCardFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val bounds = RectF()
    private val clip = Path()
    // 10dp matches the physical card radius in the two-column grid without cutting its title edge.
    private val radius = resources.displayMetrics.density * 10f

    /** Matches the exposed rounded corners to white- and black-bordered physical printings. */
    fun updateCardBackdrop(drawable: Drawable?) {
        setBackgroundColor(if (hasWhitePrintedBorder(drawable)) Color.WHITE else DARK_BACKDROP)
    }

    private fun hasWhitePrintedBorder(drawable: Drawable?): Boolean {
        if (drawable == null || drawable is CardLoadingDrawable) return false
        val bitmap = Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val previousBounds = Rect(drawable.bounds)
        return try {
            drawable.setBounds(0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
            drawable.draw(canvas)
            var bright = 0
            var visible = 0
            val inset = 2
            val horizontal = intArrayOf(9, 16, 24, 32, 39)
            val vertical = intArrayOf(11, 22, 33, 44, 55)
            horizontal.forEach { x ->
                intArrayOf(bitmap.getPixel(x, inset), bitmap.getPixel(x, SAMPLE_HEIGHT - inset - 1))
                    .forEach { color ->
                        if (Color.alpha(color) >= 180) {
                            visible++
                            if (isWarmWhite(color)) bright++
                        }
                    }
            }
            vertical.forEach { y ->
                intArrayOf(bitmap.getPixel(inset, y), bitmap.getPixel(SAMPLE_WIDTH - inset - 1, y))
                    .forEach { color ->
                        if (Color.alpha(color) >= 180) {
                            visible++
                            if (isWarmWhite(color)) bright++
                        }
                    }
            }
            visible >= 12 && bright * 100 >= visible * 60
        } catch (_: RuntimeException) {
            false
        } finally {
            drawable.bounds = previousBounds
            bitmap.recycle()
        }
    }

    private fun isWarmWhite(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return red >= 185 && green >= 185 && blue >= 175 &&
            maxOf(red, green, blue) - minOf(red, green, blue) <= 55
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        if (width <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // A Magic card is roughly 63 × 88 mm. Deriving height from the actual column width avoids
        // fixed dp heights that leave unused gutters on wider phones and tablets.
        val naturalHeight = (width * CARD_HEIGHT_RATIO).roundToInt()
        val cardHeight = when (View.MeasureSpec.getMode(heightMeasureSpec)) {
            View.MeasureSpec.EXACTLY -> View.MeasureSpec.getSize(heightMeasureSpec)
            View.MeasureSpec.AT_MOST -> minOf(naturalHeight, View.MeasureSpec.getSize(heightMeasureSpec))
            else -> naturalHeight
        }
        super.onMeasure(
            widthMeasureSpec,
            View.MeasureSpec.makeMeasureSpec(cardHeight, View.MeasureSpec.EXACTLY)
        )
    }

    override fun draw(canvas: Canvas) {
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.rewind()
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        val checkpoint = canvas.save()
        canvas.clipPath(clip)
        super.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    private companion object {
        const val CARD_HEIGHT_RATIO = 88f / 63f
        const val SAMPLE_WIDTH = 48
        const val SAMPLE_HEIGHT = 67
        val DARK_BACKDROP: Int = Color.rgb(32, 32, 32)
    }
}
