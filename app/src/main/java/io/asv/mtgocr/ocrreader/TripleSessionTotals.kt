package io.asv.mtgocr.ocrreader

/** Includes encounters no longer visible in the 20-thumbnail strip; updates never add twice. */
internal class TripleSessionTotals {
    data class Item(val count:Int,val amount:Double?,val currency:String)
    private val items=linkedMapOf<Long,Item>()
    fun update(event:Long,count:Int,amount:Double?,currency:String) {
        if(count<=0) items.remove(event)
        else items[event]=Item(count,amount?.takeIf { it.isFinite() && it>=0 },currency)
    }
    fun total(convert:(Double,String)->Double):Double = items.values.fold(java.math.BigDecimal.ZERO) { sum,item ->
        sum+(item.amount?.let { convert(it,item.currency).toBigDecimal()*item.count.toBigDecimal() } ?: java.math.BigDecimal.ZERO)
    }.toDouble()
    val pending:Int get()=items.values.filter { it.amount==null }.sumOf { it.count }
}
