package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Same geometry as OCR scanner; the surface transforms these VIEW pixels in the current AR frame. */
class TripleTitleGuide(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var caption:String?=null
    private var version=0
    fun showName(name:String?) {
        caption=name;val token=++version;invalidate()
        if(name!=null) postDelayed({ if(token==version) { caption=null;invalidate() } },2500)
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val b=OcrTitleRegion.forFrame(width,height)
        paint.color=Color.argb(35,0,210,230);paint.style=Paint.Style.FILL
        canvas.drawRoundRect(RectF(b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat()),8f,8f,paint)
        paint.color=Color.CYAN;paint.style=Paint.Style.STROKE;paint.strokeWidth=2*resources.displayMetrics.density
        canvas.drawRoundRect(RectF(b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat()),8f,8f,paint)
        paint.style=Paint.Style.FILL;paint.textSize=12*resources.displayMetrics.scaledDensity
        paint.setShadowLayer(3f,0f,1f,Color.BLACK)
        val label=caption?.let { context.getString(R.string.triple_title_found,it) } ?: context.getString(R.string.triple_title_guide)
        val display=android.text.TextUtils.ellipsize(label,android.text.TextPaint(paint),(b.right-b.left).toFloat(),android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(display,b.left.toFloat(),(b.bottom+paint.textSize+6).coerceAtMost(height-paint.textSize),paint)
        paint.clearShadowLayer()
    }
    companion object {
        fun points(width:Int,height:Int):FloatArray {
            val b=OcrTitleRegion.forFrame(width,height)
            return floatArrayOf(b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat(),b.left.toFloat(),b.bottom.toFloat())
        }
    }
}
