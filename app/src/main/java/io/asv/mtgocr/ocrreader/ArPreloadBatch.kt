package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo

data class ArPreloadCard(val printing: String,val image: String,val label: String,val priceText: String,val rarity:String="",val setCode:String="",val collectorNumber:String="")
object ArPreloadBatch {
    fun latestGroup(cards: List<CardInfo>): String? = cards.flatMap { card ->
        card.groups.filter { it.matches(Regex("Cornelius [0-9]+")) }.map { it to card.addedAt }
    }.maxByOrNull { it.second }?.first
    fun select(cards: List<CardInfo>,group: String): List<CardInfo> = cards
        .filter { group in it.groups && !it.printingUuid.isNullOrBlank() && !it.imgPath.isNullOrBlank() }
        .distinctBy { it.printingUuid }.sortedBy { it.printingUuid }
    // MTGJSON has two UUIDs for prepare/adventure halves sharing one printed image.
    // Keep different collector numbers/variants, but never train identical halves twice.
    fun selectSet(cards: List<io.asv.mtgocr.ocrreader.data.SetCardOption>) = cards
        .sortedBy { it.printingUuid }.distinctBy { Triple(it.setCode,it.collectorNumber,it.imageUrl) }
    fun cacheIdentity(cards: List<ArPreloadCard>)=cards.sortedBy { it.printing }.joinToString("\n") { it.printing+"|"+it.image }
}
