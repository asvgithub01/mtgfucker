package io.asv.mtgocr.ocrreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_history", primaryKeys = ["printingUuid", "finish", "provider", "priceDate", "amount", "currency", "refreshAt"])
data class PriceHistoryEntity(val printingUuid: String, val finish: String, val provider: String,
    val priceDate: String, val amount: Double, val currency: String, val observedAt: Long,
    // Zero identifies a deduplicated provider quote; positive values identify explicit refreshes.
    val refreshAt: Long = 0)

@Entity(tableName = "group_price_history")
data class GroupPriceHistoryEntity(@PrimaryKey val id: String, val library: String, val scope: String,
    val provider: String, val observedAt: Long, val currency: String, val total: Double,
    val missing: Int, val composition: String)

/** Compare like with like: membership/quantity/condition/coverage and currency must match. */
object PriceHistoryPolicy {
    fun delta(previous: Double?, current: Double): Pair<Double, Double?>? = previous?.let {
        (current - it) to if (it == 0.0) null else (current - it) / it * 100.0
    }
    fun comparable(a: GroupPriceHistoryEntity, b: GroupPriceHistoryEntity): Boolean =
        a.library == b.library && a.scope == b.scope && a.provider == b.provider &&
            a.currency == b.currency && a.composition == b.composition && a.missing == b.missing
}
