package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        if (width <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // A Magic card is roughly 63 × 88 mm. Deriving height from the actual column width avoids
        // fixed dp heights that leave unused gutters on wider phones and tablets.
        val cardHeight = (width * CARD_HEIGHT_RATIO).roundToInt()
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
    }
}
