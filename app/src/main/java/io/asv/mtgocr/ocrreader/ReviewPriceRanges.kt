package io.asv.mtgocr.ocrreader

/** Compare like finishes and EUR quotes only. No imputed price for missing languages/variants. */
internal object ReviewPriceRanges {
    data class Quote(val printing:String,val language:String,val amount:Double)
    data class Spread(val euros:Double,val percent:Double?)
    data class Result(val editions:Spread?,val languages:Spread?,val priced:Int,val total:Int)
    fun spread(values:List<Double>):Spread? {
        val valid=values.filter { it.isFinite() && it>=0 }
        if(valid.size<2) return null
        val low=valid.min();val delta=valid.max()-low
        return Spread(delta,if(low>0)delta/low*100 else if(delta==0.0)0.0 else null)
    }
    fun calculate(quotes:List<Quote>,total:Int):Result {
        fun largest(groups:Collection<List<Quote>>):Spread? {
            val ranges=groups.mapNotNull { spread(it.map(Quote::amount)) }
            if(ranges.isEmpty())return null
            return Spread(ranges.maxOf { it.euros },ranges.mapNotNull { it.percent }.maxOrNull())
        }
        return Result(largest(quotes.groupBy { it.language }.values),largest(quotes.groupBy { it.printing }.values),quotes.size,total)
    }
    fun matches(result:Result?,mode:Int,minEuros:Double,minPercent:Double):Boolean {
        if(minEuros<=0 && minPercent<=0)return true
        val choices=when(mode) {1->listOfNotNull(result?.editions);2->listOfNotNull(result?.languages);else->listOfNotNull(result?.editions,result?.languages)}
        return choices.any { it.euros>=minEuros && (minPercent<=0 || (it.percent!=null && it.percent>=minPercent)) }
    }
}
