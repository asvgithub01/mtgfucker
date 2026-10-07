package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.*
import android.view.View

/** Perspective Canvas overlay follows the AR image pose; hides immediately on tracking loss. */
class EdScannerArOverlay(context: Context): View(context) {
    @Volatile var points: FloatArray?=null
    @Volatile var genericPoints: FloatArray?=null
    @Volatile var price=""
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val transform=Matrix()
    private val source=floatArrayOf(0f,0f,252f,0f,252f,352f,0f,352f)
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if(points==null) genericPoints?.takeIf { it.size==8 }?.let { quad ->
            val path=Path().apply {
                moveTo(quad[0],quad[1]); for(i in 1..3) lineTo(quad[i*2],quad[i*2+1]); close()
            }
            paint.style=Paint.Style.STROKE; paint.strokeWidth=3f*resources.displayMetrics.density
            paint.color=0xFFFFC247.toInt(); canvas.drawPath(path,paint)
        }
        val target=points ?: return
        if(target.size!=8 || !transform.setPolyToPoly(source,0,target,0,4)) return
        canvas.save(); canvas.concat(transform)
        paint.color=Color.CYAN; paint.style=Paint.Style.STROKE; paint.strokeWidth=3f
        canvas.drawRoundRect(2f,2f,250f,350f,10f,10f,paint)
        paint.style=Paint.Style.FILL; paint.color=0xE6151820.toInt()
        canvas.drawRoundRect(8f,252f,244f,342f,10f,10f,paint)
        paint.color=Color.WHITE; paint.textSize=13f; paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
        val lines=price.lines().filter { it.isNotBlank() }.take(3)
        lines.forEachIndexed { index,line ->
            val text=android.text.TextUtils.ellipsize(line,android.text.TextPaint(paint),220f,android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(text,16f,274f+index*23,paint)
        }
        canvas.restore()
        // Camera pose callbacks already schedule draws while tracking.
    }
}
