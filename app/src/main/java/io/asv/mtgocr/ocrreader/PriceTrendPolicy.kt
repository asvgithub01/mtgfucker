package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.PriceHistoryEntity
import io.asv.mtgocr.ocrreader.data.PriceHistoryPolicy

/** A chart is always one printing, finish, supplier and native currency. No cross-source deltas. */
object PriceTrendPolicy {
    data class Point(val date: String, val amount: Double, val timestamp: Long? = null)
    fun points(rows: List<PriceHistoryEntity>, uuid: String, finish: String, provider: String, currency: String): List<Point> =
        rows.filter { it.printingUuid == uuid && it.finish == finish && it.provider == provider &&
            it.currency == currency && it.amount.isFinite() && it.amount >= 0 &&
            it.priceDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }.let { valid ->
            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val quotes = valid.filter { it.refreshAt == 0L }.groupBy { it.priceDate }.map { (date, samples) ->
                Point(date, samples.maxBy { it.observedAt }.amount, day.parse(date)!!.time)
            }
            val refreshes = valid.filter { it.refreshAt > 0L }.distinctBy { it.refreshAt }.map {
                Point(time.format(java.util.Date(it.refreshAt)), it.amount, it.refreshAt)
            }
            (quotes + refreshes).sortedBy { it.timestamp }
        }
    fun delta(points: List<Point>): Pair<Double, Double?>? = if (points.size < 2) null
        else PriceHistoryPolicy.delta(points[points.lastIndex-1].amount,points.last().amount)
}
