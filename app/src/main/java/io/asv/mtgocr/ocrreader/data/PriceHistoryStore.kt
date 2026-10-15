package io.asv.mtgocr.ocrreader.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors

object PriceHistoryStore {
    private val worker = Executors.newSingleThreadExecutor()
    /** Called on repository workers; original provider dates, never fabricated legacy dates. */
    fun recordQuotes(context: Context, prices: List<CardPriceEntity>) {
        val now = System.currentTimeMillis()
        CardDatabase.get(context).cardDao().savePriceHistory(prices.filter {
            it.amount.isFinite() && it.amount >= 0 && it.priceDate.isNotBlank()
        }.map { PriceHistoryEntity(it.printingUuid,SelectedPrices.finish(it.finish),it.provider,it.priceDate,it.amount,it.currency,now) })
    }

    internal fun refreshObservations(prices: List<CardPriceEntity>, now: Long): List<PriceHistoryEntity> =
        prices.filter { it.amount.isFinite() && it.amount >= 0 && it.priceDate.isNotBlank() }
            .map { PriceHistoryEntity(it.printingUuid, SelectedPrices.finish(it.finish), it.provider,
                it.priceDate, it.amount, it.currency, now, refreshAt = now) }.distinct()

    /** Capture immutable membership/values before dispatch, not mutable CardInfo objects. */
    @JvmStatic fun recordCollection(context: Context, library: String, cards: List<CardInfo>, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        val provider = cards.mapNotNull { SelectedPrices.quote(app,it)?.provider }.distinct().sorted().joinToString("+")
            .ifBlank { PriceSourcePreferences.selectedId(app) }
        val now = System.currentTimeMillis()
        val currency = PriceCurrency.preferred(app)
        data class Entry(val signature: String, val quantity: Int, val value: Double?, val groups: List<String>, val source: String?)
        val entries = cards.filter { it.quantityCount > 0 }.map {
            val quote = SelectedPrices.quote(app,it)
            val amount = PriceCurrency.amountOrNull(app,it)
            Entry(listOf(it.collectionItemId,it.printingUuid,SelectedPrices.finish(it.finish),it.quantityCount,it.condition,amount != null,quote?.provider,quote?.currency,quote?.let { q -> PriceCurrency.convert(app,1.0,q.currency) }).joinToString("\u001f"),
                it.quantityCount,amount,it.groups.toList(),quote?.provider)
        }
        val observations = refreshObservations(cards.filter { it.quantityCount > 0 }
            .mapNotNull { SelectedPrices.quote(app, it) }, now)
        val scopes = linkedMapOf("all" to entries)
        entries.flatMap { it.groups }.distinct().forEach { group -> scopes["group:$group"] = entries.filter { group in it.groups } }
        val rows = scopes.map { (scope, members) ->
            val signature = members.map { it.signature }.sorted().joinToString("\u001e")
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            GroupPriceHistoryEntity(UUID.randomUUID().toString(),library,scope,members.mapNotNull { it.source }.distinct().sorted().joinToString("+").ifBlank { provider },now,currency,
                members.sumOf { (it.value ?: 0.0) * it.quantity },members.filter { it.value == null }.sumOf { it.quantity },digest)
        }
        worker.execute {
            val success = runCatching {
                CardDatabase.get(app).runInTransaction {
                    val dao = CardDatabase.get(app).cardDao()
                    dao.savePriceHistory(observations)
                    dao.saveGroupPriceHistory(rows)
                }
            }.isSuccess
            Handler(Looper.getMainLooper()).post { done(success) }
        }
    }
}
