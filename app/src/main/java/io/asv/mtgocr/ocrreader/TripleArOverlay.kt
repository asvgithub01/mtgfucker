package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.*
import android.view.View

/** Perspective Canvas overlay follows the AR image pose; hides immediately on tracking loss. */
class TripleArOverlay(context: Context): View(context) {
    data class Card(val printing:String,val points:FloatArray,val price:String,val color:Int)
    data class CardImage(val bitmap:Bitmap,val caption:String)
    @Volatile var cards=emptyList<Card>()
    var languages=emptyMap<String,String>()
    var imageEnabled=false
    var images=emptyMap<String,CardImage>()
    var loading=emptySet<String>()
    private val placeholder=CardLoadingDrawable()
    @Volatile var points: FloatArray?=null
    @Volatile var genericPoints: FloatArray?=null
    @Volatile var borderColor=TripleRarityColor.color(null)
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
        val visible=cards
        if(visible.isNotEmpty()) { visible.forEach { drawCard(canvas,it) };return }
        val target=points ?: return
        drawCard(canvas,Card("",target,price,borderColor))
    }
    private fun drawCard(canvas:Canvas,card:Card) {
        val target=card.points
        if(target.size!=8 || !transform.setPolyToPoly(source,0,target,0,4)) return
        canvas.save(); canvas.concat(transform)
        if(imageEnabled) {
            val entry=images[card.printing]
            if(entry!=null) {
                paint.alpha=255;paint.isFilterBitmap=true
                canvas.drawBitmap(entry.bitmap,null,RectF(0f,0f,252f,352f),paint)
                paint.color=0xCC151820.toInt();paint.style=Paint.Style.FILL
                canvas.drawRect(8f,230f,244f,250f,paint)
                paint.color=Color.WHITE;paint.textSize=10f
                canvas.drawText(entry.caption,12f,244f,paint)
            } else if(card.printing in loading) {
                placeholder.setBounds(0,0,252,352);placeholder.draw(canvas)
            }
        }
        // Separate from the reference-image caption: only the physically observed language.
        val language=TripleLanguageBadge.label(languages[card.printing])
        paint.style=Paint.Style.FILL;paint.textSize=17f;paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
        val badgeWidth=paint.measureText(language)+16f
        paint.color=0xDD151820.toInt();canvas.drawRoundRect(8f,8f,8f+badgeWidth,36f,6f,6f,paint)
        paint.color=Color.WHITE;canvas.drawText(language,16f,28f,paint)
        paint.color=card.color; paint.style=Paint.Style.STROKE; paint.strokeWidth=4f
        canvas.drawRoundRect(2f,2f,250f,350f,10f,10f,paint)
        paint.style=Paint.Style.FILL; paint.color=0xE6151820.toInt()
        canvas.drawRoundRect(8f,252f,244f,342f,10f,10f,paint)
        paint.color=Color.WHITE; paint.textSize=13f; paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
        val lines=card.price.lines().filter { it.isNotBlank() }.take(3)
        lines.forEachIndexed { index,line ->
            val text=android.text.TextUtils.ellipsize(line,android.text.TextPaint(paint),220f,android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(text,16f,274f+index*23,paint)
        }
        canvas.restore()
        // Camera pose callbacks already schedule draws while tracking.
    }
}
