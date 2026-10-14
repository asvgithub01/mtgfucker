package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import io.asv.mtgocr.ocrreader.data.*
import java.util.Locale
import java.util.concurrent.Executors

class PriceHistorySection @JvmOverloads constructor(context: Context, attrs: AttributeSet?=null) : LinearLayout(context,attrs) {
    private val title=label(18f).apply { setText(R.string.price_history_title);setTypeface(typeface,Typeface.BOLD) }
    private val source=Spinner(context).apply {
        contentDescription=context.getString(R.string.price_history_source)
        backgroundTintList=android.content.res.ColorStateList.valueOf(Color.LTGRAY)
        setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(29,32,39)))
    }
    private val trend=label(19f).apply { setTypeface(typeface,Typeface.BOLD) }
    private val chart=PriceHistoryChart(context)
    private val note=label(12f).apply { setText(R.string.price_history_chart_note);setTextColor(Color.LTGRAY) }
    private var generation=0
    init {
        orientation=VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12));setBackgroundColor(Color.rgb(29,32,39))
        source.visibility=GONE;chart.visibility=GONE;trend.setText(R.string.price_history_empty)
        addView(title);addView(source,LayoutParams(-1,dp(48)));addView(trend)
        addView(chart,LayoutParams(-1,dp(240)));addView(note)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun label(size:Float)=TextView(context).apply { textSize=size;setTextColor(Color.WHITE);setPadding(0,dp(4),0,dp(4)) }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    fun bind(uuid:String, finish:String, current:CardPriceEntity?=null) {
        val request=++generation
        val app=context.applicationContext
        val order=PriceSourcePreferences.priorityIds(context)
        val normalized=SelectedPrices.finish(finish)
        trend.setTextColor(Color.LTGRAY);trend.setText(R.string.price_details_loading)
        chart.submit(emptyList(),"EUR");chart.visibility=GONE;source.visibility=GONE
        worker.execute {
            val result=runCatching {
                val dao=CardDatabase.get(app).cardDao()
                val local=dao.pricesFor(listOf(uuid))+dao.snapshotPricesFor(listOf(uuid)).map {
                    CardPriceEntity(it.printingUuid,it.finish,it.amount,it.currency,it.provider,it.priceDate,it.updatedAt)
                }
                val quote=current?.takeIf { it.printingUuid==uuid && SelectedPrices.finish(it.finish)==normalized }
                    ?: SelectedPrices.choose(local,order,uuid,normalized)
                if(quote!=null) PriceHistoryStore.recordQuotes(app,listOf(quote))
                quote to dao.priceHistoryForPrinting(uuid,normalized)
            }
            post {
                if(request!=generation) return@post
                result.fold({ (quote,rows) -> render(uuid,normalized,quote,rows,order) },{
                    trend.setText(R.string.price_history_error)
                })
            }
        }
    }
    internal fun render(uuid:String, finish:String, quote:CardPriceEntity?, rows:List<PriceHistoryEntity>, order:List<String>) {
        val series=rows.map { it.provider to it.currency }.distinct().sortedBy { pair ->
            if(pair.first==quote?.provider && pair.second==quote.currency) -1 else order.indexOf(pair.first).let { if(it<0) Int.MAX_VALUE else it }
        }
        if(series.isEmpty()) { trend.setText(R.string.price_history_empty);return }
        val finishLabel=when(finish) { "nonfoil" -> context.getString(R.string.nonfoil);"foil" -> context.getString(R.string.foil);else -> finish }
        source.visibility=VISIBLE
        source.adapter=ArrayAdapter(context,R.layout.price_history_source_item,series.map {
            "${PriceSourcePresentation.label(context,it.first)} · ${it.second} · $finishLabel"
        }).also { it.setDropDownViewResource(R.layout.price_history_source_item) }
        fun display(index:Int) {
            if(index !in series.indices)return
            val pair=series[index]
            val points=PriceTrendPolicy.points(rows,uuid,finish,pair.first,pair.second)
            chart.visibility=if(points.isEmpty())GONE else VISIBLE
            chart.submit(points,pair.second)
            val delta=PriceTrendPolicy.delta(points)
            if(delta==null) { trend.setTextColor(Color.LTGRAY);trend.setText(R.string.price_history_need_two);return }
            trend.setTextColor(when { delta.first>0 -> Color.rgb(98,220,146);delta.first<0 -> Color.rgb(255,119,129);else -> Color.LTGRAY })
            val arrow=when { delta.first>0 -> "↑";delta.first<0 -> "↓";else -> "=" }
            val percent=delta.second?.let { String.format(Locale.getDefault(),"%+.2f%% · ",it) }.orEmpty()
            trend.text="$arrow $percent"+String.format(Locale.getDefault(),"%+.2f %s",delta.first,pair.second)
            trend.contentDescription=context.getString(R.string.price_history_change,trend.text)
        }
        source.onItemSelectedListener=SimpleItemSelectedListener(::display)
        source.setSelection(0);display(0)
    }
    override fun onDetachedFromWindow() { generation++;super.onDetachedFromWindow() }
    companion object { private val worker=Executors.newSingleThreadExecutor() }
}
