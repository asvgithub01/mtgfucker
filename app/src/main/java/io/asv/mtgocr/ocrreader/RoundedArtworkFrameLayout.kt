package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** A clipped, wide frame for Scryfall's artwork-only crop. */
class RoundedArtworkFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val bounds = RectF()
    private val clip = Path()
    private val radius = resources.displayMetrics.density * 13f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        if (width <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        super.onMeasure(
            widthMeasureSpec,
            View.MeasureSpec.makeMeasureSpec((width * ART_HEIGHT_RATIO).roundToInt(), View.MeasureSpec.EXACTLY)
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
        const val ART_HEIGHT_RATIO = 9f / 16f
    }
}
