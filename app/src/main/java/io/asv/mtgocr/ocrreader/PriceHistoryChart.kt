package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

/** Native, read-only chart; all plotted samples are actual stored observations. */
class PriceHistoryChart @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context,attrs) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var points = emptyList<PriceTrendPolicy.Point>()
    private var currency = "EUR"
    private var times = emptyList<Long>()
    fun submit(values: List<PriceTrendPolicy.Point>, currency: String) {
        points=values;this.currency=currency
        val parser=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply { timeZone=TimeZone.getTimeZone("UTC");isLenient=false }
        times=values.mapIndexed { index, it -> runCatching { parser.parse(it.date)!!.time }.getOrDefault(index.toLong()) }
        contentDescription=values.joinToString("; ") { "${it.date}: ${it.amount} $currency" }
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(29,32,39))
        if(points.isEmpty()) return
        val symbol=when(currency) { "EUR" -> "€"; "USD" -> "$"; else -> currency }
        val smallest=points.minOf { it.amount };val largest=points.maxOf { it.amount }
        val margin=max((largest-smallest)*.12,max(largest*.02,.05))
        val rawStep=(largest-smallest+2*margin)/4
        val magnitude=10.0.pow(floor(log10(rawStep)))
        val step=listOf(1.0,2.0,2.5,5.0,10.0).first { it*magnitude>=rawStep }*magnitude
        val minimum=max(0.0,floor((smallest-margin)/step)*step)
        val maximum=ceil((largest+margin)/step)*step
        val ticks=((maximum-minimum)/step).roundToInt().coerceAtLeast(1)
        ink.textSize=11*resources.displayMetrics.scaledDensity
        val left=ink.measureText(String.format(Locale.getDefault(),"%.2f %s",maximum,symbol))+10*density
        val right=width-12*density;val top=14*density;val bottom=height-66*density
        if(right<=left || bottom<=top) return
        fun x(index:Int):Float = if(points.size==1) (left+right)/2 else
            left+(right-left)*((times[index]-times.first()).toDouble()/max(1L,times.last()-times.first())).toFloat()
        fun y(value:Double)=bottom-(bottom-top)*((value-minimum)/(maximum-minimum)).toFloat()
        ink.strokeWidth=density
        for(i in 0..ticks) {
            val value=minimum+step*i
            val yy=y(value)
            ink.color=Color.rgb(60,63,70);canvas.drawLine(left,yy,right,yy,ink)
            ink.color=Color.LTGRAY;ink.textAlign=Paint.Align.RIGHT
            canvas.drawText(String.format(Locale.getDefault(),"%.2f %s",value,symbol),left-7*density,yy+4*density,ink)
        }
        val labels=(0..minOf(4,points.lastIndex)).map { if(points.size==1) 0 else it*points.lastIndex/minOf(4,points.lastIndex) }.distinct()
        labels.forEach { index ->
            val xx=x(index)
            ink.color=Color.rgb(60,63,70);canvas.drawLine(xx,top,xx,bottom,ink)
            canvas.save();canvas.rotate(-42f,xx,bottom+12*density)
            ink.color=Color.LTGRAY;ink.textAlign=Paint.Align.RIGHT
            canvas.drawText(points[index].date,xx,bottom+12*density,ink);canvas.restore()
        }
        val path=Path()
        points.forEachIndexed { index, p -> if(index==0)path.moveTo(x(index),y(p.amount)) else path.lineTo(x(index),y(p.amount)) }
        ink.color=Color.WHITE;ink.style=Paint.Style.STROKE;ink.strokeWidth=2*density
        canvas.drawPath(path,ink);ink.style=Paint.Style.FILL
        points.forEachIndexed { index,p -> canvas.drawCircle(x(index),y(p.amount),3*density,ink) }
    }
}
