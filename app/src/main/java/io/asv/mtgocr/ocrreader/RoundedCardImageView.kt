package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * Clips the drawable itself to the physical outline of an MTG card.
 *
 * Some scanned printings are rectangular JPEGs with a white matte behind the card's rounded
 * corners. A rounded background/outline on the ImageView is not enough when the drawable uses
 * fitCenter, because the visible image may occupy only part of the view. Computing the drawable's
 * transformed bounds also keeps the mask aligned in fullscreen while pinching and panning.
 */
open class RoundedCardImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private val visibleCardBounds = RectF()
    private val cardClipPath = Path()
    private val edgeInset = resources.displayMetrics.density * .35f

    override fun onDraw(canvas: Canvas) {
        val currentDrawable = drawable
        if (currentDrawable == null || currentDrawable.bounds.isEmpty) {
            super.onDraw(canvas)
            return
        }

        visibleCardBounds.set(currentDrawable.bounds)
        imageMatrix.mapRect(visibleCardBounds)
        // ImageView applies its drawable matrix after translating into the padded content area.
        visibleCardBounds.offset(paddingLeft.toFloat(), paddingTop.toFloat())
        visibleCardBounds.inset(edgeInset, edgeInset)
        if (visibleCardBounds.width() <= 0f || visibleCardBounds.height() <= 0f) {
            super.onDraw(canvas)
            return
        }

        // A real Magic card's corner radius is roughly 5% of its width. Masking a fraction farther
        // in also removes the pale JPEG antialiasing halo instead of leaving a one-pixel white arc.
        val radius = min(visibleCardBounds.width() * .07f, visibleCardBounds.height() * .05f)
        cardClipPath.rewind()
        cardClipPath.addRoundRect(visibleCardBounds, radius, radius, Path.Direction.CW)
        val checkpoint = canvas.save()
        canvas.clipPath(cardClipPath)
        super.onDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}
