package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.data.SelectedPrices

/** Draw on the untransformed (fit-center) Glide image; legacy/manual cards have no location. */
class PhotoPriceImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ZoomableImageView(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var labels = emptyList<Pair<PhotoCardBounds, String>>()

    fun showPrices(cards: List<PhotoScanCard>) {
        labels = cards.mapNotNull { card ->
            val bounds = card.photoBounds ?: return@mapNotNull null
            val quote = SelectedPrices.quote(context, card.printingUuid, card.finish)
            bounds to (quote?.let { PriceCurrency.format(context,
                PriceCurrency.convert(context, it.amount, it.currency), PriceCurrency.preferred(context)) } ?: "—")
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = drawable ?: return
        if(image !is android.graphics.drawable.BitmapDrawable && image !is com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable) return
        if (image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
        val density = resources.displayMetrics.density
        paint.textSize = 13f * resources.displayMetrics.scaledDensity
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        labels.forEach { (bounds, text) ->
            val point = floatArrayOf((bounds.left + bounds.right) / 2 * image.intrinsicWidth,
                (bounds.top + bounds.bottom) / 2 * image.intrinsicHeight)
            imageMatrix.mapPoints(point)
            val half = paint.measureText(text) / 2 + 5 * density
            val x = point[0] + paddingLeft
            if(x < -half || x > width + half) return@forEach
            val y = point[1] + paddingTop
            paint.color = 0xDD000000.toInt()
            canvas.drawRoundRect(x - half, y + paint.ascent() - 3 * density,
                x + half, y + paint.descent() + 3 * density, 4 * density, 4 * density, paint)
            paint.color = Color.WHITE
            canvas.drawText(text, x - paint.measureText(text) / 2, y, paint)
        }
    }
}
