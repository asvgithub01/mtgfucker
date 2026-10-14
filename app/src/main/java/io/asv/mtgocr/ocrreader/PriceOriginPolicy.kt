package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardPriceEntity

/** Match the displayed base price, not today's unrelated price or another finish. */
internal object PriceOriginPolicy {
    data class Input(val uuid: String, val finish: String, val amount: Double?, val currency: String,
                     val copies: Int, val scryfallId: String? = null)
    data class Origin(val provider: String, val date: String)
    fun matching(input: Input, rows: List<CardPriceEntity>): Origin? {
        val amount = input.amount?.takeIf { it.isFinite() && it >= 0 } ?: return null
        fun finish(value: String) = if (value == "normal") "nonfoil" else value
        return rows.filter {
            it.printingUuid == input.uuid && finish(it.finish) == finish(input.finish) &&
                it.currency.equals(input.currency, true) && it.amount.isFinite() &&
                kotlin.math.abs(it.amount - amount) < 0.005 && it.provider.isNotBlank() &&
                it.priceDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
        }.map { Origin(it.provider, it.priceDate) }.distinct().singleOrNull()
    }
}
