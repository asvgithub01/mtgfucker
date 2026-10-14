package io.asv.mtgocr.ocrreader.data

/** MTGJSON parts are not extra physical cards. Never merge by title, art or number alone. */
internal object PhysicalPrintings {
    data class Group(val primary: CardPrintingEntity, val parts: List<CardPrintingEntity>) {
        val uuids: Set<String> get() = parts.mapTo(linkedSetOf()) { it.uuid }
    }

    fun groups(printings: List<CardPrintingEntity>): List<Group> = printings.groupBy {
        it.scryfallId?.takeIf(String::isNotBlank)?.let { id -> "scryfall:$id" } ?: "uuid:${it.uuid}"
    }.values.map { parts ->
        val ordered = parts.sortedWith(compareBy<CardPrintingEntity> {
            when (it.side) { "a" -> 0; null -> 1; else -> 2 }
        }.thenBy { it.uuid })
        Group(ordered.first(), ordered)
    }

    fun needsFaceMetadata(printings: List<CardPrintingEntity>): Boolean =
        groups(printings).any { it.parts.size > 1 && it.parts.any { part -> part.side == null } }

    /** Read-only aliases: preserve real collection item IDs, quantities, languages and finishes. */
    fun <T> ownedByVisiblePrinting(cards: List<SetCardOption>, owned: Map<String, T>): Map<String, T> =
        cards.mapNotNull { card ->
            (owned[card.printingUuid] ?: card.printingUuids.firstNotNullOfOrNull { owned[it] })
                ?.let { card.printingUuid to it }
        }.toMap()
}
