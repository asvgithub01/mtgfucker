package io.asv.mtgocr.ocrreader.data

import io.asv.mtgocr.ocrreader.CardFinish
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.util.Locale

data class CardmarketExportRow(
    val name: String,
    val setCode: String,
    val setName: String,
    val foil: Boolean,
    val quantity: Int,
    val priceEur: Double,
    val condition: String,
    val language: String,
)

data class CardmarketExportBatch(
    val setCode: String,
    val setName: String,
    val number: Int,
    val total: Int,
    val rows: List<CardmarketExportRow>,
) {
    val copyCount: Int get() = rows.sumOf { it.quantity }
}

data class CardmarketExportPlan(
    val batches: List<CardmarketExportBatch>,
    val skippedCount: Int,
) {
    val eligibleCount: Int get() = batches.sumOf { it.rows.size }
}

/** Produces CSV files understood by the Cardmarket Bulk Import browser extension. */
object CardmarketCsvExporter {
    const val MAX_ROWS_PER_BATCH = 100
    private const val HEADER =
        "Name,Set code,Foil,Signed,Rarity,Quantity,Purchase price,Condition,Language,Comment"
    private val supportedLanguages = setOf(
        "en", "fr", "de", "es", "it", "zhs", "ja", "pt", "ru", "ko", "zht",
        "nl", "pl", "cs", "hu", "id", "th",
    )

    @JvmStatic
    fun prepare(
        cards: List<CardInfo>,
        pricesEurByCollectionItemId: Map<String, Double>,
    ): CardmarketExportPlan {
        var skipped = 0
        val grouped = linkedMapOf<String, MutableList<CardmarketExportRow>>()
        cards.forEach { card ->
            val name = card.name.orEmpty().trim()
            val setCode = card.setCode.orEmpty().trim().uppercase(Locale.ROOT)
            val language = CardLanguage.toCode(card.languageCode)
            val price = pricesEurByCollectionItemId[card.collectionItemId]
            if (name.isEmpty() || setCode.isEmpty() || language !in supportedLanguages ||
                price == null || !price.isFinite() || price <= 0.0
            ) {
                skipped++
                return@forEach
            }
            grouped.getOrPut(setCode) { mutableListOf() }.add(
                CardmarketExportRow(
                    name = name,
                    setCode = setCode,
                    setName = card.setName.orEmpty().trim(),
                    foil = CardFinish.isFoil(card.finish),
                    quantity = card.quantityCount,
                    priceEur = price,
                    condition = card.condition,
                    language = language,
                ),
            )
        }

        val batches = grouped.flatMap { (setCode, rows) ->
            val chunks = rows.chunked(MAX_ROWS_PER_BATCH)
            chunks.mapIndexed { index, chunk ->
                CardmarketExportBatch(
                    setCode = setCode,
                    setName = chunk.firstOrNull()?.setName.orEmpty(),
                    number = index + 1,
                    total = chunks.size,
                    rows = chunk,
                )
            }
        }
        return CardmarketExportPlan(batches, skipped)
    }

    @JvmStatic
    fun toCsv(rows: List<CardmarketExportRow>): String = buildString {
        append(HEADER).append("\r\n")
        rows.forEach { row ->
            appendCsv(row.name).append(',')
            appendCsv(row.setCode).append(',')
            append(if (row.foil) "foil" else "normal").append(',')
            append("no,,")
            append(row.quantity).append(',')
            append(String.format(Locale.US, "%.2f", row.priceEur)).append(',')
            appendCsv(row.condition).append(',')
            appendCsv(row.language).append(',')
            append("\r\n")
        }
    }

    /** A trial listing must never inherit a multi-copy quantity from the collection. */
    @JvmStatic
    fun singleCopy(row: CardmarketExportRow): CardmarketExportRow = row.copy(quantity = 1)

    private fun StringBuilder.appendCsv(value: String): StringBuilder {
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            append('"').append(value.replace("\"", "\"\"")).append('"')
        } else {
            append(value)
        }
        return this
    }
}
