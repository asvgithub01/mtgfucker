package io.asv.mtgocr.ocrreader.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** No Room/network access on binding. Unknown legacy amounts are never relabelled. */
object SelectedPrices {
    private data class Value(val quote: CardPriceEntity?)
    private val values = ConcurrentHashMap<String, Value>()
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0L
    fun finish(value: String?) = value.orEmpty().ifBlank { "nonfoil" }.let { if (it == "normal") "nonfoil" else it }
    private fun key(provider: String, uuid: String, finish: String?) = "$provider|$uuid|${finish(finish)}"
    @JvmStatic fun invalidate() { generation++; values.clear() }
    @JvmStatic fun quote(context: Context, card: CardInfo): CardPriceEntity? =
        quote(context, card.printingUuid.orEmpty(), card.finish)
    @JvmStatic fun quote(context: Context, uuid: String, finish: String?): CardPriceEntity? =
        values[key(PriceSourcePreferences.orderKey(context), uuid, finish)]?.quote
    fun publish(context: Context, uuid: String, finish: String, quote: CardPriceEntity?) {
        val provider = PriceSourcePreferences.orderKey(context)
        if (quote != null && quote.provider !in PriceSourcePreferences.priorityIds(context)) return
        if (quote == null) values.remove(key(provider, uuid, finish)) else values[key(provider, uuid, finish)] = Value(quote)
    }
    internal fun choose(rows: List<CardPriceEntity>, provider: String, uuid: String, finish: String): CardPriceEntity? =
        rows.filter { it.provider == provider && it.printingUuid == uuid && finish(it.finish) == finish(finish) &&
            it.amount.isFinite() && it.amount >= 0 }.maxWithOrNull(compareBy<CardPriceEntity> { it.priceDate }.thenBy { it.updatedAt })

    internal fun choose(rows: List<CardPriceEntity>, order: List<String>, uuid: String, finish: String): CardPriceEntity? =
        order.firstNotNullOfOrNull { provider -> choose(rows,provider,uuid,finish) }

    @JvmStatic fun ensure(context: Context, cards: List<CardInfo>, ready: Runnable) {
        ensurePrintings(context, cards.map { it.printingUuid.orEmpty() to finish(it.finish) }, ready)
    }

    fun ensurePrintings(context: Context, printings: List<Pair<String, String>>, ready: Runnable) {
        val app = context.applicationContext
        val order = PriceSourcePreferences.priorityIds(app)
        val provider = order.joinToString(">")
        val epoch = generation
        val wanted = printings.filter { it.first.isNotBlank() }.distinct()
            .filter { !values.containsKey(key(provider, it.first, it.second)) }
        if (wanted.isEmpty()) return
        worker.execute {
            val result = runCatching {
                val dao = CardDatabase.get(app).cardDao()
                val rows = wanted.map { it.first }.filter { it.isNotBlank() }.distinct().chunked(400).flatMap { batch ->
                    dao.snapshotPricesFor(batch).filter { it.provider in order }.map {
                        CardPriceEntity(it.printingUuid,it.finish,it.amount,it.currency,it.provider,it.priceDate,it.updatedAt)
                    } + dao.pricesFor(batch).filter { it.provider in order }
                }.groupBy { it.printingUuid }
                wanted.associate { (uuid, finish) -> key(provider,uuid,finish) to Value(choose(rows[uuid].orEmpty(),order,uuid,finish)) }
                    .also { selected -> PriceHistoryStore.recordQuotes(app,selected.values.mapNotNull { it.quote }) }
            }
            if (epoch == generation && result.isSuccess) {
                values.putAll(result.getOrThrow())
                Handler(Looper.getMainLooper()).post { if (epoch == generation) ready.run() }
            }
        }
    }
}
