package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.app.AlertDialog
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.data.SelectedPrices
import io.asv.mtgocr.ocrreader.data.CardPriceEntity
import io.asv.mtgocr.ocrreader.data.PriceMath
import io.asv.mtgocr.ocrreader.data.PriceSourcePreferences
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.util.concurrent.Executors

/** Read-only, no network or collection writes. Snapshot displayed inputs on the UI thread. */
object PriceDetailsDialog {
    private val worker = Executors.newSingleThreadExecutor()
    @JvmStatic fun show(activity: Activity, cards: List<CardInfo>) = showCards(activity, cards, null, null)
    @JvmStatic fun showCollection(activity: Activity, cards: List<CardInfo>, library: String, scope: String) =
        showCards(activity,cards,library,scope)
    private fun showCards(activity: Activity, cards: List<CardInfo>, library: String?, scope: String?) {
        showInputs(activity, cards.map { card ->
            val quote = SelectedPrices.quote(activity,card)
            PriceOriginPolicy.Input(card.printingUuid.orEmpty(), SelectedPrices.finish(card.finish),
                quote?.amount,quote?.currency.orEmpty(),card.quantityCount)
        }, library, scope)
    }
    internal fun showInputs(activity: Activity, source: List<PriceOriginPolicy.Input>, library: String? = null, scope: String? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val inputs = source.filter { it.copies > 0 }.toList()
        val builder = AlertDialog.Builder(activity).setTitle(R.string.price_details_title)
            .setMessage(R.string.price_details_loading).setPositiveButton(R.string.close, null)
        if (library != null && (scope == "all" || scope?.startsWith("group:") == true)) {
            builder.setNeutralButton(R.string.price_history_title) { _, _ -> PriceHistoryDialog.showGroup(activity, library, scope!!) }
        }
        val dialog = builder.show()
        val order = PriceSourcePreferences.priorityIds(activity)
        val app = activity.applicationContext
        worker.execute {
            val result = runCatching {
                val dao = CardDatabase.get(app).cardDao()
                val ids = inputs.mapNotNull { it.scryfallId?.takeIf(String::isNotBlank) }.distinct()
                val printings = ids.chunked(400).flatMap { dao.printingsByScryfallIds(it) }.groupBy { it.scryfallId }
                val uuids = (inputs.map { it.uuid } + printings.values.flatten().map { it.uuid })
                    .filter(String::isNotBlank).distinct()
                val prices = uuids.chunked(400).flatMap { batch ->
                    dao.pricesFor(batch) + dao.snapshotPricesFor(batch).map {
                        CardPriceEntity(it.printingUuid,it.finish,it.amount,it.currency,it.provider,it.priceDate,it.updatedAt)
                    }
                }.filter { it.provider in order }.groupBy { it.printingUuid }
                val groups = linkedMapOf<PriceOriginPolicy.Origin, Int>()
                var unknown = 0
                var missing = 0
                for (input in inputs) {
                    if (input.amount == null) { missing += input.copies; continue }
                    val possible = if (input.uuid.isNotBlank()) listOf(input.uuid)
                        else printings[input.scryfallId].orEmpty().map { it.uuid }
                    val origin = possible.mapNotNull { uuid ->
                        PriceOriginPolicy.matching(input.copy(uuid = uuid), listOfNotNull(SelectedPrices.choose(prices[uuid].orEmpty(),order,uuid,input.finish)))
                    }.distinct().singleOrNull()
                    if (origin == null) unknown += input.copies
                    else groups[origin] = (groups[origin] ?: 0) + input.copies
                }
                buildList {
                    add(app.getString(R.string.price_details_reference_note))
                    if (inputs.isEmpty()) add(app.getString(R.string.price_details_empty))
                    for ((origin, count) in groups) {
                        val provider = PriceSourcePreferences.available.firstOrNull { it.id == origin.provider }
                            ?.label(app) ?: origin.provider
                        add(app.getString(R.string.price_details_line, provider, origin.date, count))
                    }
                    if (unknown > 0) add(app.getString(R.string.price_details_unknown, unknown))
                    if (missing > 0) add(app.getString(R.string.price_details_missing, missing))
                }.joinToString("\n\n")
            }
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed && dialog.isShowing)
                    dialog.setMessage(result.getOrElse { activity.getString(R.string.price_details_error) })
            }
        }
    }
}
