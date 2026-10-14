package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.PriceHistoryEntity
import io.asv.mtgocr.ocrreader.data.PriceHistoryPolicy

/** A chart is always one printing, finish, supplier and native currency. No cross-source deltas. */
object PriceTrendPolicy {
    data class Point(val date: String, val amount: Double)
    fun points(rows: List<PriceHistoryEntity>, uuid: String, finish: String, provider: String, currency: String): List<Point> =
        rows.filter { it.printingUuid == uuid && it.finish == finish && it.provider == provider &&
            it.currency == currency && it.amount.isFinite() && it.amount >= 0 &&
            it.priceDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            .groupBy { it.priceDate }.map { (date, observations) ->
                Point(date, observations.maxBy { it.observedAt }.amount)
            }.sortedBy { it.date }
    fun delta(points: List<Point>): Pair<Double, Double?>? = if (points.size < 2) null
        else PriceHistoryPolicy.delta(points[points.lastIndex-1].amount,points.last().amount)
}
