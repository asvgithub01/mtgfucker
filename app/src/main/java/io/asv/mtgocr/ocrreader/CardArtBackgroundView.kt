package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * Uses only the illustration window near the top of a regular Magic card as a full-screen
 * backdrop. Showing the complete portrait card made the frame, rules box and tiny text dominate
 * the blur, so the translucent controls had almost no visible depth behind them.
 */
class CardArtBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private val artMatrix = Matrix()

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post(::updateArtMatrix)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateArtMatrix()
    }

    private fun updateArtMatrix() {
        val source = drawable ?: return
        if (width <= 0 || height <= 0 || source.intrinsicWidth <= 0 || source.intrinsicHeight <= 0) return

        val sourceWidth = source.intrinsicWidth.toFloat()
        val sourceHeight = source.intrinsicHeight.toFloat()
        // Scryfall's normal/large card images use the same card-frame proportions. These bounds
        // retain the art box while excluding the name/mana line and the rules/text area.
        val artLeft = sourceWidth * ART_LEFT
        val artTop = sourceHeight * ART_TOP
        val artWidth = sourceWidth * (ART_RIGHT - ART_LEFT)
        val artHeight = sourceHeight * (ART_BOTTOM - ART_TOP)
        val scale = maxOf(width / artWidth, height / artHeight)
        val translatedX = (width - artWidth * scale) / 2f - artLeft * scale
        val translatedY = (height - artHeight * scale) / 2f - artTop * scale

        artMatrix.reset()
        artMatrix.setScale(scale, scale)
        artMatrix.postTranslate(translatedX, translatedY)
        imageMatrix = artMatrix
    }

    private companion object {
        const val ART_LEFT = .065f
        const val ART_TOP = .105f
        const val ART_RIGHT = .935f
        const val ART_BOTTOM = .47f
    }
}
